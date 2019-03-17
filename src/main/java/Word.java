/**
 * Created by IntelliJ IDEA.
 * User: wenchao.qi
 * Date: 2019/3/11
 * Time: 7:39 PM
 */
public class Word {
    private String word;
    private long index;

    public Word(String word, long index){
        this.word = word;
        this.index = index;
    }

    public String getWord() {
        return word;
    }

    public long getIndex() {
        return index;
    }

    public void setIndex(long index) {
        this.index = index;
    }

    public void setWord(String word) {
        this.word = word;
    }
}
