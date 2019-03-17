import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;


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

        //本地测试
        int totalParts = 6;
        FileOutputStream[] partFiles = new FileOutputStream[totalParts];
        for (int i = 0; i < partFiles.length; i++){
            partFiles[i] = new FileOutputStream(new File(dir, i + "part"));
        }

        //开始按照单词的hash值拆分，并将记录每个part单词索引
        long index = 0;

        try (LineIterator iterator = FileUtils.lineIterator(data, "UTF-8")){
            while (iterator.hasNext()){
                String line = iterator.nextLine();
                String[] words = line.split("[ \n\t\r.,;:!?(){}]");

                for (String word : words){
                    if (word.equals(""))
                        continue;

                    Word word1 = new Word(word.toLowerCase(), index++);
                    //根据hash分片
                    int partFileIndex = Math.abs(word1.getWord().hashCode()) % totalParts;
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append(word1.getWord());
                    stringBuilder.append(",");
                    stringBuilder.append(word1.getIndex());
                    stringBuilder.append("\n");
                    partFiles[partFileIndex].write(stringBuilder.toString().getBytes());
                    partFiles[partFileIndex].flush();

                }
            }
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
     * 功能说明：选出每个分片中index最小且不重复出现的单词。然后找出所有其中中index最小的
     *
     * @param tmpPath 拆分后文件所在路径
     * @param dataSize 原始数据文件大小，单位G
     * @param dataSize 内存大小，单位G
     */
    public static String Select(String tmpPath, int dataSize, int memSize) throws Exception{
        //创建tmpPath目录，存储拆分后文件
        File dir = new File(tmpPath);
        if (!dir.exists())
            dir.mkdirs();

//        //拆分出文件的总数
//        int totalParts = (dataSize / memSize + 1) * 2;
        //本地测试
        int totalParts = 6;

        LineIterator[] partFiles = new LineIterator[totalParts];


        HashMap<String, Long> reapeatMap = new HashMap<>();//记录重复的单词
        HashMap<String, Long> noReapeatMap = new HashMap<>();//记录不重复的单词

        //记录第一个不重复的单词
        long minIdex = Long.MAX_VALUE;
        String minWord = "";


        //遍历一遍所有的分片，找出第一个不重复的单词
        for (int i = 0; i < partFiles.length; i++){
            partFiles[i] = FileUtils.lineIterator(new File(dir, i + "part"), "UTF-8");

            //将当前分片中不重复的单词放到noReapeatMap中
            while (partFiles[i].hasNext()){
                String line = partFiles[i].nextLine();
                String[] two = line.split(",");

                String word = two[0];
                long index = Long.parseLong(two[1]);

                if (reapeatMap.containsKey(word))
                    continue;

                if (noReapeatMap.containsKey(word)){
                    noReapeatMap.remove(word);
                    reapeatMap.put(word, index);
                }
                else {
                    noReapeatMap.put(word, index);
                }
            }

            //找出当前noRepeatMap中index最小的
            for (Map.Entry<String, Long> entry : noReapeatMap.entrySet()){
                if (entry.getValue() < minIdex) {
                    minIdex = entry.getValue();
                    minWord = entry.getKey();
                }
            }

            noReapeatMap.clear();
            reapeatMap.clear();
        }

        return minWord;
    }
}
