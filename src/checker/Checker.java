package checker;

import java.io.IOException;
import java.util.List;

import util.ErrorInformation;


/**
 * 检查器的父类：抽象类
 * @author tangmh
 *
 */
public abstract class Checker {

	ErrorInformation errInfo; // 检查器的错误信息统计数据

	/**
	 * 构造方法
	 */
	public Checker() {
		errInfo = new ErrorInformation();
	}

	/**
	 * 检查器的执行方法
	 * @param stackTraceList 一个stackTrace异常信息的列表
	 * @param projectPath 项目的路径
	 * @return 是否出错
	 * @throws IOException 文件读写异常
	 */
	public abstract boolean examine(List<StackTraceElement> stackTraceList, String projectPath) throws IOException;
	
	/**
	 * 打印异常信息
	 */
	public abstract void printErrInfo();
	
	/**
	 * 打印警告
	 */
	public void warning() {
		errInfo.warning();
	}
	
}
