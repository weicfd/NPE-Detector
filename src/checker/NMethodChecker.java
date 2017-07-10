package checker;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import util.FilesUtil;

/**
 * 空对象调用了其方法的检查器
 * 思路参考@see NVarCheck
 * @author tangmh
 *
 */
public class NMethodChecker extends Checker {
	List<StackTraceElement> stackTraceList;
	String projectPath;

	/* (non-Javadoc)
	 * @see checker.Checker#examine(java.util.List, java.lang.String)
	 */
	@Override
	public boolean examine(List<StackTraceElement> stackTraceList,
			String projectPath) throws IOException {
		this.stackTraceList = stackTraceList;
		this.projectPath = projectPath;

//		System.out.println("NMETHOD ANALYSIS");
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
		lineInfo = deleteCommet(lineInfo);

		Pattern pt = Pattern
				.compile("\\s*([\\w\\.\\(\\)\"]*)\\.\\w+\\([\\w\\.\\\"]*(\\([\\w\\.\\\"]*\\))*[\\w\\.\\\"]*\\);");
//		System.out.println(pt.pattern());
		Matcher matcher = pt.matcher(lineInfo);

		if (matcher.find()) {
//			System.out.println(lineInfo);
			String possibleNullObj = matcher.group(1);
			String targetObj = "";
			boolean isMemberVar = false;
			String targetType = null;

			if (possibleNullObj.contains(".")) {
//				System.out.println("多重调用");
				if (possibleNullObj.matches("\\s*this\\.\\w+")) {
					targetObj = possibleNullObj.replaceAll("\\s*this\\.", "");
				}else {
					targetObj = possibleNullObj;
				}
				// 多重调用
				// A.B.C 可能性只讨论3种
				// A.B，A不是NULL，B是A的NULL成员变量
				// A.B 是函数返回值NULL
				// A本身是NULL，A.B即报错
//				System.out.println(targetObj);
				String[] A = targetObj.split("\\.", 2);  // 只分割首个
//				System.out.println(A[1]);
				String AType = searchforType(A[0], fileLine);
//				System.out.println(AType);
				if (checkIfIsNull(A[0], lineInfo)) {
					// 输出信息： A是NULL
//					System.out.println("A is null");
					return true;
				}else if (A.length < 2) {
//					System.out.println("length < 2");
					return false;
				}else {
					String B[] = A[1].split("\\.", 2);
					String BType = typeReturn(B[0], AType);
					while (B.length > 1) {
//						System.out.println(B[0]);
						// TODO while内部的这一段建议重构
						if (B[0].endsWith(")")) {
							// A.B()型
//							System.out.println(B[1]);
							System.out.println("检查" + AType + "的" + B[0]);
							functionExitCheck(AType, B[0]);
							B = B[1].split("\\.", 2); 
						}else {
							// A.B型
							if(!ensureTarget(A[0], B[0])){
								// output
								return true;
							}
						}
					}
					System.out.println("检查" + BType + "的" + B[0]);
					functionExitCheck(BType, B[0]);
					errInfo.warning += "\n建议检查:\n"+ lineInfo +"\n中的" + B[0] + "函数\n";
					return false;
				}
			}else {
				targetObj = possibleNullObj;
			}
				targetType = searchforType(targetObj, fileLine); // 静态查找：object的类型
//				System.out.println("目标类型 " + targetType);
				// 向上查找初始化语句
				

				int paramPos = -1; // 记录调用方法的参数列表中该变量的位置，在递归中会用到，初始时没有递归调用，赋值0
				String methodName = "", methodParam;

				for (StackTraceElement stackTraceElement : stackTraceList) {
//					System.out.println("定位到目标： " + targetObj + "对象");
					// 寻找文件
					String path = projectPath + "//"
							+ stackTraceElement.getFileName();
					List<String> lines;
					try {
						lines = FilesUtil.readTextFileByLines(path);
					} catch (NoSuchFileException e) {
						path = FilesUtil.findFile(stackTraceElement.getFileName(), new File(projectPath));
						lines = FilesUtil.readTextFileByLines(path);
					}


					if (paramPos != -1) {
						// 上层的搜索
						// Assertion：递归行一定是方法调用语句行
						pt = Pattern
								.compile(methodName
										+ "\\((([\\w\\s\\[\\]]*,\\s*)*([\\w\\s\\[\\]]*))\\)");
						lineInfo = lines
								.get(stackTraceElement.getLineNumber() - 1);
						lineInfo = deleteCommet(lineInfo);

						matcher = pt.matcher(lineInfo);
						if (matcher.find()) {
							methodParam = matcher.group(1);
							String[] params = methodParam.split("\\s*,\\s*");
							targetObj = params[paramPos];
							// System.out.println("New target: " + targetObj);
						}

					}

					// 定位方法
					String methodRegEx; // 判断方法声明的正则表达式
					boolean isInit = false;
					if (stackTraceElement.getMethodName() == "<init>") {
						methodName = stackTraceElement.getClassName();
						methodRegEx = "\\s*(p\\w+)\\s+" + methodName
								+ "\\(([\\w\\s\\d\\[\\],_]*)\\)";
						isInit = true;
					} else {
						methodName = stackTraceElement.getMethodName();
						methodRegEx = "\\s*p\\w+\\s+(static|final)?\\s*\\w+\\s+"
								+ methodName
								+ "\\(\\s*(\\w[\\w\\s\\[\\]\\,_]*)\\)";
					}

					Pattern linePattern = Pattern.compile(methodRegEx);
					Matcher lineMatcher;
					int i = stackTraceElement.getLineNumber() - 1;
					
					while (i > 0) {
						String line = lines.get(i - 1);
						line = deleteCommet(line);
						lineMatcher = linePattern.matcher(line);

						if (!isMemberVar && line.contains(targetObj)) {
							// 检测是否是数组初始化句
							if (checkIfIsDeclare(line, targetType, targetObj)) {
								// 找到初始化错误
								errInfo.errLineNo = i;
								errInfo.errMethodName = stackTraceElement
										.getMethodName();
								errInfo.errFileName = stackTraceElement
										.getFileName();
								errInfo.errLine = line;
								errInfo.errInfo = "调用方法的对象没有正确初始化";
								return true;
							} else if (checkIfIsInit(line, targetType, isInit)) {
								return false;
							}
						}

						if (!isMemberVar && lineMatcher.find()) { // 已匹配到方法头部
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
							} else {
								// 可能是该类的成员变量
								isMemberVar = true;
								i = 1;
								break;
							}
						} else {
							i--; // 向上检测
						}

					}

					if (isMemberVar) {
						int state = 0;
						String firstMethodRegEx = "\\s*p\\w+\\s+(static|final)?\\s*\\w+\\s+"
								+ "\\w+\\(\\s*(\\w[\\w\\s\\[\\]\\,_]*)\\).*";
						while (i < stackTraceElement.getLineNumber()) {
							String line = lines.get(i - 1);
							line = deleteCommet(line);

							switch (state) {
							case 0:
								linePattern = Pattern.compile(firstMethodRegEx);
								lineMatcher = linePattern.matcher(line);
								if (line.contains(targetObj)) {
									if (checkIfIsDeclare(line, targetType,
											targetObj)) {
										// 确认为该类的成员变量
										// 寻找构造方法，若构造方法中有初始化操作 return false
										state = 1;
										i = 1;
										continue;
									}
								}

								if (lineMatcher.find()) {
									// 不是成员变量
									return false;
								}
								break;
							case 1:
								// 匹配构造方法
								linePattern = Pattern.compile("\\s*(p\\w+)\\s+"
										+ stackTraceElement.getClassName()
										+ "\\(([\\w\\s\\d\\[\\],_]*)\\).*");
								lineMatcher = linePattern.matcher(line);
								if (lineMatcher.matches()) {
									errInfo.errLineNo = i;
									errInfo.errMethodName = stackTraceElement
											.getMethodName();
									errInfo.errFileName = stackTraceElement
											.getFileName();
									errInfo.errLine = line;
									state = 2;
									break;
								}
								if (i == lines.size()) {
									// 若未找到构造方法
									errInfo.errInfo = "类的成员变量未初始化且没有找到构造方法";
									return true;
								}
								break;

							case 2:
								// 构造方法内
								if (line.contains("}")) {
									errInfo.errInfo = "类的成员变量在构造方法内未初始化";
									return true;
								} else if (line.contains(targetObj)) {
									if (checkIfIsInit(line, targetType, false)) {
										return false;
									}
								}
								break;
							default:
								break;
							}

							i++;
						}
					}

				}

		}
		return false;
	}

	/**
	 * 去除行后注释
	 * @param line 给出一行的信息
	 * @return 取出行后注释的字符串
	 */
	private String deleteCommet(String line) {
		if (line.split("//").length > 1) {
			line = line.split("//")[0];
		}
		return line;
	}

	/**
	 * 检查是否是正确的对象初始化表达
	 * @param line 要检查的行
	 * @param targetType 待对象的类型
	 * @param inInit 该对象是否处于构造方法中
	 * @return 正确表达为TRUE，否则FALSE
	 */
	private boolean checkIfIsInit(String line, String targetType, boolean inInit) {
		Pattern p = Pattern.compile("new\\s+" + targetType + "\\(");
		if (p.matcher(line).find()) {
			return true;
		} else if (inInit) {
			p = Pattern.compile("(super|this)\\(");
			if (p.matcher(line).find()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 检测是否是的对象的未初始化的声明语句
	 * @param line 要检查的行
	 * @param targetType 待对象的类型
	 * @param inInit 该对象是否处于构造方法中
	 * @return 正确表达为TRUE，否则FALSE
	 */
	private boolean checkIfIsDeclare(String line, String targetType,
			String targetObj) {
		Pattern p = Pattern.compile("\\s*(private|public|protected)?\\s*"
				+ targetType + "\\s+" + targetObj + "\\s*(\\=\\s*null\\s*)?;");
		Matcher m = p.matcher(line);
		if (m.find()) {
			return true;
		}
		return false;
	}


	/**
	 * 静态查找：object的类型
	 * @param possibleNullObj 要查找的变量名
	 * @param fileLine 被查找的文件的各行
	 * @return 类型名
	 */
	private String searchforType(String possibleNullObj, List<String> fileLine) {
		String reg1 = "\\s*p\\w+\\s+(static|final)?\\s*\\w+\\s+"
				+ "\\w+\\((.*(\\s|,))?([\\w<>]+)\\s+" + possibleNullObj
				+ "((\\s|,).*)?\\).*";
		String reg2 = "\\s*(private|public|protected)?\\s*([\\w<>]+)\\s+"
				+ possibleNullObj + "(\\s*=.*\\s*)?;";

		Pattern p1 = Pattern.compile(reg1);
		Pattern p2 = Pattern.compile(reg2);
		for (String line : fileLine) {
			if (line.contains(possibleNullObj)) {
				Matcher m1 = p1.matcher(line);
				if (m1.matches()) {
					return m1.group(4);
				} else {
					Matcher m2 = p2.matcher(line);
					if (m2.matches()) {
						return m2.group(2);
					}
				}
			}
		}

		return null;
	}
	

	/**
	 * 找到targetType类的method的返回类型 
	 * @param methodName 等待查找返回类型的方法名
	 * @param targetType 调用该方法的类型名
	 * @return 方法的返回类型
	 * @throws IOException 文件读写错误
	 */
	private String typeReturn(String methodName, String targetType) throws IOException {
		String path = projectPath + "//"
				+ targetType + ".java";
		List<String> lines;
		try {
			lines = FilesUtil.readTextFileByLines(path);
		} catch (NoSuchFileException e) {
			path = FilesUtil.findFile(targetType + ".java", new File(projectPath));
			lines = FilesUtil.readTextFileByLines(path);
		}
		
		methodName = methodName.replaceFirst("\\([\\w\\.\\\"]*(\\([\\w\\.\\\"]*\\))*[\\w\\.\\\"]*\\)", "");
		for (String line : lines) {
			line = deleteCommet(line);
			Pattern p = Pattern.compile("\\s*(private|public|protected)\\s+(\\w+)\\s+" + methodName
					+ "\\s*\\(.*\\{");
			Matcher m = p.matcher(line);
			if (m.find()) {
				return m.group(2);
			}
		}
		return null;
	}

	/**
	 * TODO 建议重构
	 * 确认目标是否为NULL
	 * 为了确认一个变量是否为NULL，复用了源程序的代码
	 * @param object 要确认的变量的变量名
	 * @param reline 该变量出现的那一行，用于改造后复用源程序代码
	 * @return 是NULL值为TRUE，否则FALSE
	 * @throws IOException
	 */
	private boolean checkIfIsNull(String object, String reline) throws IOException {
		// A.* 改造为 A.B;
		Pattern p = Pattern.compile("\\s*(\\w+)(\\.[\\w\\.\\(\\)\\[\\]]+)");
		Matcher m = p.matcher(reline);
		if (m.find()) {
			String toReplaces = m.group(2);
			reline.replace(toReplaces, ".B;");
		}
		// 寻找文件
				String filePath = projectPath + "//"
						+ stackTraceList.get(0).getFileName();
				List<String> fileLine;
				try {
					fileLine = FilesUtil.readTextFileByLines(filePath);
				} catch (NoSuchFileException e) {
					filePath = FilesUtil.findFile(stackTraceList.get(0).getFileName(),
							new File(projectPath));
					fileLine = FilesUtil.readTextFileByLines(filePath);
				}

				// 定位到行
				String lineInfo = reline;
				// 去除行后注释
				lineInfo = deleteCommet(lineInfo);

				Pattern pt = Pattern
						.compile("\\b([\\w\\.]+)\\.(\\w+)[\\s\\+\\-\\)\\}\\]!*/=;]");
				Matcher matcher = pt.matcher(lineInfo);

				if (matcher.find()) {
					String possibleNullObj = matcher.group(1);
					String calledVar = matcher.group(2);
					String targetObj, targetType;
					boolean isMemberVar = false;targetType = searchforType(possibleNullObj, fileLine); // 静态查找：object的类型
					// System.out.println("目标类型 " + targetType);
					if (ensureTarget(targetType, calledVar)) {
						// 确认calledVar是该obj的变量，向上查找初始化语句
						targetObj = possibleNullObj;
						int paramPos = -1;
						String methodName = "", methodParam;

						for (StackTraceElement stackTraceElement : stackTraceList) {
							// System.out.println("定位到目标： " + targetObj + "对象");
							// 寻找文件
							String path = projectPath + "//"
									+ stackTraceElement.getFileName();
							List<String> lines;
							try {
								lines = FilesUtil.readTextFileByLines(path);
							} catch (NoSuchFileException e) {
								path = FilesUtil.findFile(stackTraceElement
										.getFileName(), new File(projectPath));
								lines = FilesUtil.readTextFileByLines(path);
							}

							if (paramPos != -1) {
								// 上层的搜索
								// Assertion：递归行一定是方法调用语句行
								pt = Pattern
										.compile(methodName
												+ "\\((([\\w\\s\\[\\]]*,\\s*)*([\\w\\s\\[\\]]*))\\)");
								lineInfo = lines.get(stackTraceElement
										.getLineNumber() - 1);
								matcher = pt.matcher(lineInfo);
								if (matcher.find()) {
									methodParam = matcher.group(1);
									String[] params = methodParam
											.split("\\s*,\\s*");
									targetObj = params[paramPos];
									// System.out.println("New target: " +
									// targetObj);
								}

							}

							// 定位方法
							String methodRegEx;
							boolean isInit = false;
							if (stackTraceElement.getMethodName() == "<init>") {
								methodName = stackTraceElement.getClassName();
								methodRegEx = "\\s*(p\\w+)\\s+" + methodName
										+ "\\(([\\w\\s\\d\\[\\],_]*)\\)";
								isInit = true;
							} else {
								methodName = stackTraceElement.getMethodName();
								methodRegEx = "\\s*p\\w+\\s+(static|final)?\\s*\\w+\\s+"
										+ methodName
										+ "\\(\\s*(\\w[\\w\\s\\[\\]\\,_]*)\\)";
							}

							Pattern linePattern = Pattern.compile(methodRegEx);
							Matcher lineMatcher;
							int i = stackTraceElement.getLineNumber() - 1;
							
							while (i > 0) {
								String line = lines.get(i - 1);
								line = deleteCommet(line);
								lineMatcher = linePattern.matcher(line);

								if (!isMemberVar && line.contains(targetObj)) {
									// 检测是否是数组初始化句
									if (checkIfIsDeclare(line, targetType,
											targetObj)) {
										// 找到初始化错误
										errInfo.errLineNo = i;
										errInfo.errMethodName = stackTraceElement
												.getMethodName();
										errInfo.errFileName = stackTraceElement
												.getFileName();
										errInfo.errLine = line;
										errInfo.errInfo = "调用变量的对象没有正确初始化";
										return true;
									} else if (checkIfIsFunctionAssign(line, targetObj)) {
										String[] expr = line.split("=\\s*");
										String type = searchforType(expr[1].split(".")[0], lines);
										functionExitCheck(type, expr[1].split(".")[1]);
										
									} else if (checkIfIsInit(line, targetType,
											isInit)) {
										return false;
									}
								}

								if (!isMemberVar && lineMatcher.find()) { // 已匹配到方法头部
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
									} else {
										// 可能是该类的成员变量
										isMemberVar = true;
										i = 1;
										break;
									}
								} else {
									i--; // 向上检测
								}

							}

							if (isMemberVar) {
								int state = 0;
								String firstMethodRegEx = "\\s*p\\w+\\s+(static|final)?\\s*\\w+\\s+"
										+ "\\w+\\(\\s*(\\w[\\w\\s\\[\\]\\,_]*)\\).*";
								while (i < stackTraceElement.getLineNumber()) {
									String line = lines.get(i - 1);
									line = deleteCommet(line);

									switch (state) {
									case 0:
										linePattern = Pattern
												.compile(firstMethodRegEx);
										lineMatcher = linePattern.matcher(line);
										if (line.contains(targetObj)) {
											if (checkIfIsDeclare(line, targetType,
													targetObj)) {
												// 确认为该类的成员变量
												// 寻找构造方法，若构造方法中有初始化操作 return false
												state = 1;
												i = 1;
												continue;
											}
										}

										if (lineMatcher.find()) {
											// 不是成员变量
											return false;
										}
										break;
									case 1:
										// 匹配构造方法
										linePattern = Pattern
												.compile("\\s*(p\\w+)\\s+"
														+ stackTraceElement
																.getClassName()
														+ "\\(([\\w\\s\\d\\[\\],_]*)\\).*");
										lineMatcher = linePattern.matcher(line);
										if (lineMatcher.matches()) {
											errInfo.errLineNo = i;
											errInfo.errMethodName = stackTraceElement
													.getMethodName();
											errInfo.errFileName = stackTraceElement
													.getFileName();
											errInfo.errLine = line;
											state = 2;
											break;
										}
										if (i == lines.size()) {
											// 若未找到构造方法
											errInfo.errInfo = "类的成员变量未初始化且没有找到构造方法";
											return true;
										}
										break;

									case 2:
										// 构造方法内
										if (line.contains("}")) {
											errInfo.errInfo = "类的成员变量在构造方法内未初始化";
											return true;
										} else if (line.contains(targetObj)) {
											if (checkIfIsInit(line, targetType,
													false)) {
												return false;
											}
										}
										break;
									default:
										break;
									}

									i++;
								}
							}

						}

					}
				}
					
			return false;
	}
	
	/**
	 * 检查行中是否是返回值赋值表达，即 A= f() 型
	 * @param line 该行信息
	 * @param targetObj A的名字，即被赋值的变量名
	 * @return 是为TRUE，否则FALSE
	 */
	private boolean checkIfIsFunctionAssign(String line, String targetObj) {
		// 检测
		Pattern p = Pattern.compile("\\s*(\\w+\\s+)?" + targetObj
				+ "\\s*=\\s*[\\w\\.\\(\\)\\[\\]]*"
				+ "\\.\\w+\\([\\w\\s\\,\\[\\]]*\\)\\s*;");
		if (p.matcher(line).find()) {
			return true;
		}
		return false;
	}
	
	/**
	 * 确认calledVar是targetType 的成员变量，向上查找初始化语句
	 * @param targetType  要检查的类的类名
	 * @param calledVar 被检查的变量的变量名
	 * @return 是成员变量则为TRUE，否则为FALSE
	 * @throws IOException 文件读写异常
	 */
	private boolean ensureTarget(String targetType, String calledVar) throws IOException {
		String path = projectPath + "//"
				+ targetType + ".java";
		List<String> lines;
		try {
			lines = FilesUtil.readTextFileByLines(path);
		} catch (NoSuchFileException e) {
			path = FilesUtil.findFile(targetType + ".java", new File(projectPath));
			lines = FilesUtil.readTextFileByLines(path);
		}
		
		for (String line : lines) {
			line = deleteCommet(line);
			if (line.contains(calledVar)) {
				return true;
			}
		}
		errInfo.errFileName = targetType;
		return false;
	}


	/**
	 * a = f();
	 * TODO 检查一个函数是否可能返回的是NULL值，一个初步的判断
	 * 此处只能给WARNING！
	 * @param classType 调用该函数的类的类名
	 * @param ObjCallFunction 函数名
	 * @throws IOException 读写异常
	 */
	private void functionExitCheck(String classType, String ObjCallFunction) throws IOException {

		String path = projectPath + "//"
				+ classType + ".java";
		List<String> lines;
		try {
			lines = FilesUtil.readTextFileByLines(path);
		} catch (NoSuchFileException e) {
			path = FilesUtil.findFile(classType + ".java", new File(projectPath));
			lines = FilesUtil.readTextFileByLines(path);
		}
		
		Pattern p = Pattern.compile("return\\s+null|=\\s+null"); // 此处可扩展
		int i = 1;
		boolean startCheck = false;
		int noBracket = 0;
		for (String line : lines) {
			line = deleteCommet(line);
			if (line.contains(ObjCallFunction)) {
				startCheck = true;
			}
			
			if (startCheck) {
				if (line.contains("{")) {
					i++;
				}
				Matcher m = p.matcher(line);
				if (m.find()) {
					errInfo.warning += "Warning：" + classType + "类下的方法:" + ObjCallFunction
							+ "中\n第"+ i + "行可能导致返回NULL值：\n" + line + "\n";
				}
				if (line.contains("}")) {
					i--;
				}
				if (i == 0) {
					startCheck = false;
				}
			}
			
			i++;
		}
		
	}

	/* (non-Javadoc)
	 * @see checker.Checker#printErrInfo()
	 */
	@Override
	public void printErrInfo() {
		// 打印错误信息
		errInfo.print();
	}

}
