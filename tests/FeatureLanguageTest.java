import com.samsung.feature.extension.LanguageManager;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;
public class FeatureLanguageTest {
    static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        check("S Pen Air Mouse".equals(LanguageManager.text(true,"S Pen 空中鼠标")),"Mouse title");
        check("Proportional Pop-up Scaling".equals(LanguageManager.text(true,"小窗等比例缩放")),"Pop-up title");
        check("Sensitivity: 125%".equals(LanguageManager.text(true,"灵敏度：125%")),"Slider value");
        check(LanguageManager.text(true,"状态读取失败：IOException").equals("Failed to read status: IOException"),"Dynamic error");
        check(LanguageManager.text(true,"小窗等比例缩放已开启").contains("enabled"),"System status");
        check(LanguageManager.text(false,"灵敏度：125%").equals("灵敏度：125%"),"Chinese preserved");
        Pattern literals=Pattern.compile("\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
        Pattern han=Pattern.compile("[\\p{IsHan}]");
        int checked=0;
        for(String file:new String[]{"SpenMouseSettingsActivity.java","FreeformScaleSettingsActivity.java"}) {
            String source=new String(Files.readAllBytes(Paths.get("src/main/java/com/samsung/feature/extension",file)),StandardCharsets.UTF_8);
            Matcher m=literals.matcher(source);
            while(m.find()) {
                String text=m.group(1).replace("\\n","\n").replace("\\\"","\"");
                if(!han.matcher(text).find()) continue;
                String english=LanguageManager.text(true,text);
                check(!han.matcher(english).find(),file+" missing translation: "+text);
                checked++;
            }
        }
        System.out.println("FeatureLanguage: passed, "+checked+" Chinese UI literals covered");
    }
}
