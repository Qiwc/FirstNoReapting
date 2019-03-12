import org.junit.Test;

/**
 * Created by IntelliJ IDEA.
 * User: wenchao.qi
 * Date: 2019/3/11
 * Time: 6:32 PM
 */
public class TestFileImpl {
    @Test
    public void test(){
        String data = "data/test";
        String tmpPath = "data/tmp";

        FileImpl fileimpl = new FileImpl(data, tmpPath);

        try {
            fileimpl.fileSplit();
            System.out.println(fileimpl.mergeAndSelect());
        }
        catch (Exception e){
            e.printStackTrace();
        }

    }
}
