import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;


/**
 * Created by IntelliJ IDEA.
 * User: wenchao.qi
 * Date: 2019/3/11
 * Time: 6:07 PM
 */
public class FileImpl {

    /*  原始数据文件通道    */
    private FileChannel dataChannel;
    /*  tmpPath目录，存储拆分后文件   */
    private File dir;
    /*  拆分出文件的总数    */
    private int totalParts;
    /*  每个拆分文件最大允许的大小（决定内存占用量），单位B  */
    private int partSize;
    /*  分片文件的文件通道   */
    FileChannel[] partFiles;

    /**
     * 功能说明：构造函数，初始化相关参数
     *
     * @param originalData 原始数据文件名
     * @param tmpPath 拆分后文件所在路径
     */
    public FileImpl(String originalData, String tmpPath){

        /*  原始数据文件,并获取其文件通道   */
        File data = new File(originalData);
        try {
            this.dataChannel = new RandomAccessFile(data, "rw").getChannel();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        /*  创建tmpPath目录，存储拆分后文件   */
        this.dir = new File(tmpPath);
        if (!dir.exists())
            dir.mkdirs();

        /*  每个分片为1GB，为了后面内存映射方便   */
//            totalParts = (int) Math.ceil(dataSize);
//            partSize = 1 << 30;
        /*  本地测试暂时使用这个大小    */
        totalParts = 12;
        partSize = 20 ;

        /*  分片文件的文件通道   */
        partFiles = new FileChannel[totalParts];
        for (int i = 0; i < totalParts; i++){
            try {
                partFiles[i] = new RandomAccessFile(new File(dir, i + "part"), "rw").getChannel();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 功能说明：文件拆分，并将每个part排序且记录单词索引
     *
     */
    public void fileSplit() throws Exception{

        /*  分配堆外内存  */
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(partSize * 4);//读写复用同一个directbuffer
        byte[] bytes = new byte[partSize];


        /*  开始拆分，并将每个part排序且记录单词索引  */
        long index = 0;//记录是第几个单词
        List<Word> wordList = new LinkedList<>();
        String lastHalfWord = "";//每次读最后有没读完整的单词

        for (int partIndex = 0; partIndex < totalParts; partIndex++){
            /*   读取每个分片到堆外内存   */
            if (getMessageDirect(partIndex * partSize, directBuffer, bytes)){
                String text = new String(bytes, "UTF-8");
                String[] words = text.toLowerCase().split(" ");
                /*   注意两个分片读取间被分开的单词的处理   */
                String lastWord = lastHalfWord + words[0];
                if (!lastWord.equals(""))
                    wordList.add(new Word(lastWord, index++));

                for (int i = 1; i < words.length - 1; i++){
                    if (words[i].equals(""))
                        continue;
                    wordList.add(new Word(words[i], index++));
                }

                if (!words[words.length - 1].equals("")  && text.endsWith(" ")){
                    wordList.add(new Word(words[words.length - 1], index++));
                    lastHalfWord = "";
                }
                else {
                    lastHalfWord = words[words.length - 1];
                }

                /*   排序   */
                Collections.sort(wordList, new WordComparator());

                directBuffer.clear();
                /*   每个分片中，先放置一个int数，表示这个分片有多少个单词   */
                directBuffer.putInt(wordList.size());
                for (Word word : wordList){
                    /*   每个单词的存放分为三部分：1、单词长度；2、单词；3、索引   */
                    directBuffer.put((byte) word.getWord().getBytes().length);
                    directBuffer.put(word.getWord().getBytes("UTF-8"));
                    directBuffer.putLong(word.getIndex());
                }
                wordList.clear();
                /*   存储到分片中   */
                putMessageDirect(directBuffer, partIndex);
            }
        }
        dataChannel.close();
    }

    /**
     * 功能说明：文件归并，并找出第一个不重复的单词
     *
     */
    public String mergeAndSelect() throws Exception{

        MappedByteBuffer mmaps[] = new MappedByteBuffer[totalParts];//分片文件内存映射
        boolean[] empty = new boolean[totalParts];//表示文件是否读完
        int[] wordNums = new int[totalParts];//每个分片的单词量
        int[] wordCount =new int[totalParts];//记录读到当前分片第几个单词
        int emptyCount = 0;//记录读完的分片数量

        for (int i = 0; i < totalParts; i++){
            if (partFiles[i].size() > 0){
                mmaps[i] = partFiles[i].map(FileChannel.MapMode.READ_ONLY, 0, partFiles[i].size());
                empty[i] = false;
                wordNums[i] = mmaps[i].getInt();
            }
            else {
                empty[i] = true;
                emptyCount++;
            }
        }

        /*  记录第一个不重复的单词 */
        long minIdex = Long.MAX_VALUE;
        String minWord = "";

        /*  第一次，将每个分片的第一个word读到内存   */
        Word[] words = new Word[totalParts];
        for (int i = 0; i < totalParts; i++){
            if (!empty[i]){
                byte[] bytes = new byte[mmaps[i].get()];
                mmaps[i].get(bytes);
                words[i] = new Word(new String(bytes), mmaps[i].getLong());
                wordCount[i]++;
            }
        }

        /*  找出第一个不重复的单词 */
        while (emptyCount < totalParts){
            /*  找出数组中最小的word    */
            int min = 0;
            for (int i = 0; i < totalParts; i++){
                if (!empty[i] && words[i].getWord().compareTo(words[min].getWord()) < 0)
                    min = i;
            }

            /*  判断这个词有没有重复的，如果有重复就都去掉   */
            Word now = words[min];
            /*  先更新当前值  */
            if (wordCount[min] < wordNums[min]) {
                byte[] bytes = new byte[mmaps[min].get()];
                mmaps[min].get(bytes);
                words[min] = new Word(new String(bytes), mmaps[min].getLong());
                wordCount[min]++;
            }
            else {
                empty[min] = true;
                emptyCount++;
            }
            /*  查找所有分片是否有重复词，如果有则去掉 */
            long sameSize = 1;//记录相同的数量
            for (int i = 0; i < totalParts; i++){
                if (empty[i])
                    continue;
                while (words[i].getWord().equals(now.getWord()) && (wordCount[i] < wordNums[i]) ){
                    sameSize++;
                    byte[] bytes = new byte[mmaps[i].get()];
                    mmaps[i].get(bytes);
                    words[i] = new Word(new String(bytes), mmaps[i].getLong());
                    wordCount[i]++;
                }
                if (words[i].getWord().equals(now.getWord()) && !(wordCount[i] < wordNums[i]) ){
                    sameSize++;
                    empty[min] = true;
                    emptyCount++;
                }
            }

            /*  如果这个词没重复，就记录一下index */
            if (sameSize == 1){
                if (now.getIndex() < minIdex) {
                    minIdex = now.getIndex();
                    minWord = now.getWord();
                }
            }
        }

        for (FileChannel fileChannel : partFiles){
            fileChannel.close();
        }

        return minWord;
    }


    /**
     * 功能说明：FileChannel搭配DirectBuffer读取原始数据文件
     *
     * @param offset 偏移量
     * @param buffer 堆外内存
     * @param bytes 最后获取的bytes
     * @return boolean 判断是否读到了文件末尾
     */
    private boolean getMessageDirect(long offset, ByteBuffer buffer, byte[] bytes){
        buffer.clear();
        buffer.limit(partSize);//限制每次读入的量
        int size = 0;
        try {
            size = dataChannel.read(buffer, offset);
        } catch (IOException e){
            e.printStackTrace();
        }
        if (size < 0)
            return false;
        buffer.flip();
        buffer.get(bytes, 0, size);
        return true;
    }

    /**
     * 功能说明：FileChannel搭配DirectBuffer读取原始数据文件
     *
     * @param buffer 堆外内存
     * @param partIndex 第几个文件通道
     */
    private void putMessageDirect(ByteBuffer buffer, int partIndex){
        buffer.flip();
        try {
            partFiles[partIndex].write(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
