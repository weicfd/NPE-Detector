package util;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * @author tangmh
 * 文件操作工具
 */
public class FilesUtil {
	
    /**
     * 读取文件到字符串
     * @param fileName 文件路径
     * @return 文件内容
     * @throws IOException 文件读写异常
     */
    public static String readTextFile(String fileName) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(fileName)));
        return content;
    }

    /**
     * 读取文件到字符串数组
     * @param fileName 文件路径
     * @return 文件各行内容
     * @throws IOException 文件读写异常
     */
    public static List<String> readTextFileByLines(String fileName) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(fileName));
        return lines;
    }

    /**
     * 写入文本文件
     * @param fileName 文件路径
     * @param content 写入内容
     * @throws IOException 文件读写异常
     */
    public static void writeToTextFile(String fileName, String content) throws IOException {
        Files.write(Paths.get(fileName), content.getBytes(), StandardOpenOption.CREATE);
    }
    
    /**
     * 搜索指定路径下的文件
     * @param name 文件名
     * @param file 指定文件夹的File对象
     * @return 指定文件的对象，若未找到返回null
     */
    public static String findFile(String name,File file)
    {
        File[] list = file.listFiles();
        if(list!=null)
        for (File fil : list)
        {
            if (fil.isDirectory())
            {
                String s = findFile(name,fil);
            	if (s != null) {
					return s;
				}
            }
            else if (name.equalsIgnoreCase(fil.getName()))
            {
                return fil.toString();
            }
        }
        return null;
    }
    

}