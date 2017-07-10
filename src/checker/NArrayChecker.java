package checker;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import util.FilesUtil;

/**
 * 空数组检查器：
 * 错误类型：数组为空时访问其元素
 * @author tangmh
 *
 */
public class NArrayChecker extends Checker {
	

	/* (non-Javadoc)
	 * @see checker.Checker#examine(java.util.List, java.lang.String)
	 */
	@Override
	public boolean examine(List<StackTraceElement> stackTraceList,
			String projectPath) throws IOException {
		// 寻找文件
		String filePath = projectPath + "//"
				+ stackTraceList.get(0).getFileName();
		List<String> fileLine;
		try {
			fileLine = FilesUtil.readTextFileByLines(filePath);
		} catch (NoSuchFileException e) {
			filePath = FilesUtil.findFile(stackTraceList.get(0).getFileName(), new File(projectPath));
			fileLine = FilesUtil.readTextFileByLines(filePath);
		}
		
		// 定位到行
		String lineInfo = fileLine
				.get(stackTraceList.get(0).getLineNumber() - 1);
		// 去除行后注释
		if (lineInfo.split("//").length > 1) {
			lineInfo = lineInfo.split("//")[0];
		}
		Pattern pt = Pattern.compile("\\b([\\w_]+)(\\[(.+)\\])+\\W"); // 匹配数组
		Matcher matcher = pt.matcher(lineInfo);

		if (matcher.find()) {
			String targetObj = matcher.group(1);
			System.out.println("定位到目标： " + targetObj + "数组");
			int paramPos = -1;
			String methodName = "", methodParam;

			for (StackTraceElement stackTraceElement : stackTraceList) {
				// 寻找文件
				String path = projectPath + "//"
						+ stackTraceElement.getFileName();
				List<String> lines;
				try {
					lines = FilesUtil.readTextFileByLines(filePath);
				} catch (NoSuchFileException e) {
					filePath = FilesUtil.findFile(stackTraceElement.getFileName(), new File(projectPath));
					lines = FilesUtil.readTextFileByLines(filePath);
				}

				if (paramPos != -1) {
					// 上层的搜索
					// Assertion：递归行一定是方法调用语句行
					pt = Pattern.compile(methodName + 
							"\\((([\\w\\s\\[\\]]*,\\s*)*([\\w\\s\\[\\]]*))\\)");
					lineInfo = lines
							.get(stackTraceElement.getLineNumber() - 1);
					matcher = pt.matcher(lineInfo);
					if (matcher.find()) {
						methodParam = matcher.group(1);
						String[] params = methodParam.split("\\s*,\\s*");
						targetObj = params[paramPos];
//						System.out.println("New target: " +  targetObj);
					}
					
				}
				
				// 定位方法
				String methodRegEx;
				Boolean isInit = false;
				if (stackTraceElement.getMethodName() == "<init>") {
					methodName = stackTraceElement.getClassName();
					methodRegEx = "\\s*(p\\w+)\\s+" + methodName
							+ "\\(([\\w\\s\\d\\[\\],_]*)\\)";
					isInit = true;
				} else {
					methodName = stackTraceElement.getMethodName();
					methodRegEx = "\\s*p\\w+\\s+(static|final)?\\s*\\w+\\s+"
							+ methodName + "\\(\\s*(\\w[\\w\\s\\[\\]\\,_]*)\\)";
				}
				
				Pattern linePattern = Pattern.compile(methodRegEx);
				Matcher lineMatcher;
				int i = stackTraceElement.getLineNumber() - 1;
				while (i > 0) {
					String line = lines.get(i - 1);
					lineMatcher = linePattern.matcher(line);
					if (line.contains(targetObj)) {
						// 检测是否是数组初始化句
						if (checkIfIsDeclare(line, targetObj)) {
							// 找到初始化错误
							errInfo.errLineNo = i;
							errInfo.errMethodName = stackTraceElement
									.getMethodName();
							errInfo.errFileName = stackTraceElement
									.getFileName();
							errInfo.errLine = line;
							return true;
						}else if (checkIfIsInit(line, targetObj)) {
							return false;
						}
					}
					if (lineMatcher.find()) {  // 已匹配到方法头部
						if (line.contains(targetObj)) {
							methodParam = lineMatcher.group(2);
							
							// 记录参数位置
							String[] params = methodParam.split(",");
							for (int j = 0; j < params.length; j++) {
								if (params[j].contains(targetObj)) {
									paramPos = j;
									break;
								}
							}
							break;
						}else {
							return false;
						}
					} else {
						i--; // 向上检测
					}

				}
			}
		}
		return false;
	}

	/**
	 * 检测是否是正确的数组初始化表达
	 * @param line 要检查的那一行
	 * @param targetObj 要检查的数组名
	 * @return 正确表达为TRUE，否则FALSE
	 */
	private boolean checkIfIsInit(String line, String targetObj) {
		Pattern p = Pattern.compile(targetObj + "(\\[\\])*\\s*=\\s*(\\{.*\\}|new)\\s*");
		Matcher m = p.matcher(line);
		if (m.find()) {
			return true;
		}
		return false;
	}

	/**
	 * 检查是否是未初始化的数组表达
	 * @param line 要检查的那一行
	 * @param targetObj 要检查的数组名
	 * @return 正确表达为TRUE，否则FALSE
	 */
	private boolean checkIfIsDeclare(String line, String targetObj) {
		Pattern p = Pattern.compile("\\w+\\s+"+ targetObj 
				+"(\\[\\])+\\s*(=\\s*null)?\\s*;|\\w+(\\[\\])+\\s+"+ targetObj 
				+"\\s*(=\\s*null)?\\s*;");
		Matcher m = p.matcher(line);
		if (m.find()) {
			return true;
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see checker.Checker#printErrInfo()
	 */
	@Override
	public void printErrInfo() {
		// 打印错误信息
		errInfo.errInfo = "数组未初始化";
		errInfo.print();
	}

}
