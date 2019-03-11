import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;


/**
 * Created by IntelliJ IDEA.
 * User: wenchao.qi
 * Date: 2019/3/11
 * Time: 6:07 PM
 */
public class Utils {

    /**
     * 功能说明：文件拆分，并将每个part排序且记录单词索引
     *
     * @param originalData 原始数据文件名
     * @param tmpPath 拆分后文件所在路径
     * @param dataSize 原始数据文件大小，单位G
     * @param dataSize 内存大小，单位G
     */
    public static void fileSplit(String originalData, String tmpPath, int dataSize, int memSize) throws Exception{

        //原始数据文件
        File data = new File(originalData);
        //创建tmpPath目录，存储拆分后文件
        File dir = new File(tmpPath);
        if (!dir.exists())
            dir.mkdirs();

//        //拆分出文件的总数
//        int totalParts = (dataSize / memSize + 1) * 2;
//        //每个拆分文件最大允许的大小（决定内存占用量），单位B
//        long partSize = ((long) dataSize << 30) / totalParts ;

        //本地测试
        int totalParts = 6;
        long partSize = 20 ;

        FileOutputStream[] partFiles = new FileOutputStream[totalParts];
        for (int i = 0; i < partFiles.length; i++){
            partFiles[i] = new FileOutputStream(new File(dir, i + "part"));
        }

        //开始拆分，并将每个part排序且记录单词索引
        long sizeCount = 0;
        int partFileNum = 0;
        long index = 0;
        List<Word> wordList = new LinkedList<>();

        try (LineIterator iterator = FileUtils.lineIterator(data, "UTF-8")){
            while (iterator.hasNext()){
                String line = iterator.nextLine();
                sizeCount += line.getBytes("UTF-8").length;
                String[] words = line.split("[ \n\t\r.,;:!?(){}]");
                for (String word : words){
                    if (word.equals(""))
                        continue;
                    wordList.add(new Word(word.toLowerCase(), index++));
                }
                //存盘
                if (sizeCount >= partSize){
                    Collections.sort(wordList, new WordComparator());
                    for (Word word : wordList){
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append(word.getWord());
                        stringBuilder.append(",");
                        stringBuilder.append(word.getIndex());
                        stringBuilder.append("\n");
                        partFiles[partFileNum].write(stringBuilder.toString().getBytes());
                        partFiles[partFileNum].flush();
                    }
                    partFiles[partFileNum].close();
                    sizeCount = 0;
                    partFileNum++;
                    wordList.clear();
                }
            }
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
     * 功能说明：文件归并，并找出第一个不重复的单词
     *
     * @param tmpPath 拆分后文件所在路径
     * @param dataSize 原始数据文件大小，单位G
     * @param dataSize 内存大小，单位G
     */
    public static String mergeAndSelect(String tmpPath, int dataSize, int memSize) throws Exception{
        //创建tmpPath目录，存储拆分后文件
        File dir = new File(tmpPath);
        if (!dir.exists())
            dir.mkdirs();

//        //拆分出文件的总数
//        int totalParts = (dataSize / memSize + 1) * 2;
        //本地测试
        int totalParts = 6;

        LineIterator[] partFiles = new LineIterator[totalParts];
        Boolean[] empty = new Boolean[totalParts];//表示文件是否读完
        int emptyCount = 0;
        for (int i = 0; i < partFiles.length; i++){
            partFiles[i] = FileUtils.lineIterator(new File(dir, i + "part"), "UTF-8");
            if (partFiles[i].hasNext())
                empty[i] = false;
            else{
                empty[i] = true;
                emptyCount++;
            }

        }

        //记录第一个不重复的单词
        long minIdex = Long.MAX_VALUE;
        String minWord = "";

        //第一次，将每个分片的第一个word读到内存
        Word[] words = new Word[totalParts];
        for (int i = 0; i < totalParts; i++){
            if (empty[i])
                continue;
            String line = partFiles[i].nextLine();
            String[] two = line.split(",");
            words[i] = new Word(two[0], Long.parseLong(two[1]));
        }

        //找出第一个不重复的单词
        while (emptyCount < totalParts){
            //找出数组中最小的word
            int min = 0;
            for (int i = 0; i < totalParts; i++){
                if (empty[i])
                    continue;
                if (words[i].getWord().compareTo(words[min].getWord()) < 0){
                    min = i;
                }
            }

            //判断这个词有没有重复的，如果有重复就都去掉
            //先更新当前值
            Word now = words[min];
            if (partFiles[min].hasNext()) {
                String line = partFiles[min].nextLine();
                String[] two = line.split(",");
                words[min] = new Word(two[0], Long.parseLong(two[1]));
            }
            else {
                empty[min] = true;
                emptyCount++;
            }
            //查找其他文件是否有重复词，如果有则去掉
            System.out.println(now.getWord());
            long sameSize = 1;//记录相同的数量
            for (int i = 0; i < totalParts; i++){
                if (empty[i])
                    continue;
                while (words[i].getWord().equals(now.getWord()) && partFiles[i].hasNext()){
                    sameSize++;
                    String line = partFiles[i].nextLine();
                    String[] two = line.split(",");
                    words[i] = new Word(two[0], Long.parseLong(two[1]));
                }
                if (words[i].getWord().equals(now.getWord()) && !partFiles[i].hasNext()){
                    sameSize++;
                    empty[min] = true;
                    emptyCount++;
                }
            }

            //如果这个词没重复，就记录一下index
            if (sameSize == 1){
                if (now.getIndex() < minIdex) {
                    minIdex = now.getIndex();
                    minWord = now.getWord();
                    System.out.println(minWord);
                }
            }

            System.out.println("-----");
        }

        System.out.println(minWord);
        return minWord;
    }
}
