package util;


/**
 * 该类保存了异常信息
 * @author tangmh
 *
 */
public class ErrorInformation {
	public int errLineNo;  // 出错行行号
	public String errFileName; // 错误文件的文件名
	public String errMethodName; // 出错的方法名
	public String errLine; // 出错的那一行的具体信息
	public String errInfo; // 出错的类型和可能的错误点
	public String warning = ""; // 出错的警告信息
	
	/**
	 * 输出错误信息
	 */
	public void print() {
		System.out.println(errInfo);
		System.out.println("文件：" + errFileName + "中的方法：" + errMethodName);
		System.out.println("第" + errLineNo + "行出错：\n" + errLine);
	}
	
	/**
	 * 输出警告信息
	 */
	public void warning() {
		if (!warning.equals("")) {
			System.out.println(warning);
		}
	}
}
