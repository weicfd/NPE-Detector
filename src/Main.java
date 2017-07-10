import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import util.FilesUtil;
import checker.*;

/**
 * 空指针判断的主程序
 * TODO 建议修改的前提假设
 * 前提假设：
 * 1. 同一行中只有一个空指针异常错误源
 * 2. 每条语句都在同一行，即分号结尾
 * 3. 带星的注释中没有干扰程序的有效信息
 * @author tangmh 主程序
 */
public class Main {
	static String projectPath;
	final static int NARRAY = 1, NVAR = 2, NMETHOD = 3, UNKNOWN = 4;
	
	static Checker checker = null;

	/**
	 * 错误类型诊断
	 * 
	 * @param st
	 *            首行的StackTrace信息
	 * @param state
	 *            目前的类型诊断信息，若未进行过诊断该值为0，否则为当前已诊断过的最后一个错误类型的编号
	 * @return 错误类型的编号 NARRAY = 1 空指针数组 NVAR = 2 空对象的变量访问 NMETHOD = 3 空对象的方法访问
	 *         UNKNOWN = 4 未知类型
	 * @throws IOException 文件读写异常
	 */
	public static int determErrType(StackTraceElement st, int state)
			throws IOException {
		// 寻找文件
		String filePath = projectPath + "//" + st.getFileName(); 
		List<String> fileLine;
		try {
			fileLine = FilesUtil.readTextFileByLines(filePath);
		} catch (NoSuchFileException e) {
			filePath = FilesUtil.findFile(st.getFileName(), new File(projectPath));
			fileLine = FilesUtil.readTextFileByLines(filePath);
		}
		
		// 定位到行
		String lineInfo = fileLine.get(st.getLineNumber() - 1);
		// 去除行后注释
		if (lineInfo.split("//").length > 1) {
			lineInfo = lineInfo.split("//")[0];
		}
		// System.out.println(lineInfo);
		Pattern pt1 = Pattern.compile("\\b[\\w]+(\\[(.+)\\])+\\W"); // 匹配数组类错误
		Pattern pt2 = Pattern
				.compile("\\b[\\w\\.]+\\w\\.(\\w[\\w]+)[\\s\\+\\-\\)\\}\\]!*/=;]"); // 匹配变量类错误
		Pattern pt3 = Pattern
				.compile("\\b[\\w\\.]+\\w\\.(\\w[\\w]+)\\([\\w\\.,\\s]*\\)"); // 匹配方法类错误

		Matcher lineMatcher = pt1.matcher(lineInfo);
		if (state < NARRAY && lineMatcher.find()) {
			return NARRAY;
		}
		lineMatcher = pt2.matcher(lineInfo);
		if (state < NVAR && lineMatcher.find()) {
			return NVAR;
		}
		lineMatcher = pt3.matcher(lineInfo);
		if (state < NMETHOD && lineMatcher.find()) {
			return NMETHOD;
		}
		return UNKNOWN;
	}

	/**
	 * 根据一条stack trace寻找异常出错点
	 * 构造
	 * 
	 * @param stackTraceList
	 *            stack trace 元素
	 * @param errType 判断得到的错误类型
	 * @return 是否找到出错点
	 * @throws IOException 文件读写异常
	 */
	public static boolean searchObject(List<StackTraceElement> stackTraceList,
			int errType) throws IOException {
		// 寻找文件
		// 具体解析类型内错误
		switch (errType) {
		case NARRAY:
			checker = new NArrayChecker();
			break;
		case NVAR:
			checker = new NVarChecker();
			break;
		case NMETHOD:
			checker = new NMethodChecker();
			break;
		default:
			return false;
		}
		
		return checker.examine(stackTraceList, projectPath);
	}

	/**
	 * main函数
	 * 输入：projectPath，修改程序中的路径名
	 *      stackTracePath, 异常信息文件:包含出错的stackTrace中的信息
	 * 输出：异常分析信息
	 * @throws IOException 文件读写异常
	 * 
	 */
	public static void main(String[] args) throws IOException {
		// 入口
		// 读取项目文件夹、异常信息文件
//		projectPath = "//Users//tangmh//Desktop//webService//review//src";
		projectPath = "//Users//tangmh//Desktop//webService//LIbrarySystem//src";
		String stackTracePath = "stacktrace.txt"; // 异常信息路径
		// 读取异常信息文件
		List<String> exceptionLines = FilesUtil
				.readTextFileByLines(stackTracePath);

		// 读取异常信息
		Pattern headLinePattern = Pattern.compile("NullPointerException");
		int lineNo = 1;
		int headLineNo = 0;
		List<StackTraceElement> stackTrace = new ArrayList<StackTraceElement>();
		for (String line : exceptionLines) {
			// 首行定位
			if (headLineNo == 0) {
				Matcher headLineMatcher = headLinePattern.matcher(line);
				if (headLineMatcher.find()) {
					headLineNo = lineNo;
				}
			} else {
				// 解析stackTrace
				Pattern followPattern = Pattern
						.compile("\\s*at\\s+([\\w\\.$]+)\\.([\\w$<>]+)\\((.*java)?:(\\d+)\\)");
				Matcher followLineMatcher = followPattern.matcher(line);
				if (followLineMatcher.find()) {
					String className = followLineMatcher.group(1);
					String methodName = followLineMatcher.group(2);
					String sourceFile = followLineMatcher.group(3);
					int lineNum = Integer.parseInt(followLineMatcher.group(4));
					stackTrace.add(new StackTraceElement(className, methodName,
							sourceFile, lineNum));
					// System.out.println("Stack: " + stackTrace);
				}
			}
			lineNo++;
		}
		// @正则表达式 学习
		// 查找 NPE 报错行
		// System.out.println(stackTrace.get(0));
		// 根据首行进行错误类型判断
		int type = determErrType(stackTrace.get(0), 0);
		// System.out.println(type);
		while (type < UNKNOWN) {
			if (searchObject(stackTrace, type)) {
				// 找到错误
				System.out.println("错误检测信息：");
				checker.printErrInfo();
				checker.warning();
				break;
			}
			type = determErrType(stackTrace.get(0), type);
		}
		if (type == UNKNOWN) {
			checker.warning();
			System.err.println("错误检测失败");
		}
	}

}
