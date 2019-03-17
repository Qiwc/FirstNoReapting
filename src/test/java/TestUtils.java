import org.junit.Test;

/**
 * Created by IntelliJ IDEA.
 * User: wenchao.qi
 * Date: 2019/3/11
 * Time: 6:32 PM
 */
public class TestUtils {
    @Test
    public void test(){
        String data = "data/test";
        String tmpPath = "data/tmp";
        int dataSize = 100;
        int memSize = 32;
        try {
            Utils.fileSplit(data, tmpPath, dataSize, memSize);
            System.out.println(Utils.Select(tmpPath, dataSize, memSize));
        }
        catch (Exception e){
            e.printStackTrace();
        }

    }
}
