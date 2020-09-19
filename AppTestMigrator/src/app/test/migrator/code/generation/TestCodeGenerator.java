package app.test.migrator.code.generation;

import java.io.File;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.jetbrains.annotations.NotNull;

import com.google.gct.testrecorder.ui.RecordingDialog;
import com.google.gct.testrecorder.util.ResourceHelper;

import app.test.migrator.util.GradleParser;
import app.test.migrator.util.ModuleNameExtractor;

public class TestCodeGenerator {

	private static final String TEST_CODE_TEMPLATE_FILE_NAME = "TestCodeTemplate.vm";
	private static final String ESPRESSO_STANDARD_PACKAGE = "android.support.test";

	private String appDirectory;
	private String myLaunchedActivityName;
	private String className;
	private String testMethodName;
	private String packageName;
	private String resourcePackageName;
	private List<String> events;
	
	public TestCodeGenerator(String appDirectory, String myLaunchedActivityName, String className, String testMethodName, String packageName, String resourcePackageName, /*List<Object> myEvents*/ List<String> events) {
		this.appDirectory = appDirectory;
		this.myLaunchedActivityName = myLaunchedActivityName;
		this.className = className;
		this.testMethodName = testMethodName;
		this.packageName = packageName;
		this.resourcePackageName = resourcePackageName;
		this.events = events;
	}

	public void generate() throws Exception {		
		ModuleNameExtractor moduleNameExtractor = new ModuleNameExtractor();
		String moduleName = moduleNameExtractor.getModuleName(appDirectory);
		if(moduleName == null)	throw new Exception("The app structure does not satisfy our requirements!");
		
		
		String userDir = System.getProperty("user.dir"); 
		
		GradleParser gradleParser = new GradleParser();
		gradleParser.parseAndChangeGradle(appDirectory, userDir, moduleName, true, true, true, false, true, false);
		
		String testPackagePath = appDirectory + "/" + moduleName + "/src/androidTest/java/" + packageName.replaceAll("\\.", "/");
		File testPackage = new File(testPackagePath);
		if(!testPackage.exists()){
			testPackage.mkdirs();
		}
		
		Writer writer = null;
		try {
			writer = new PrintWriter(new File(testPackagePath + "/" + className + ".java"));

			VelocityEngine velocityEngine = new VelocityEngine();
			// Suppress creation of velocity.log file.
			velocityEngine.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, "org.apache.velocity.runtime.log.NullLogChute");
			velocityEngine.init();
			velocityEngine.evaluate(createVelocityContext(), writer, RecordingDialog.class.getName(), readTemplateFileContent());
			writer.flush();
		} catch (Exception e) {
			throw new RuntimeException("Failed to generate test class file: ", e);
		} finally {
			if (writer != null) {
				try {
					writer.close();
				}
				catch (Exception e) {
					// ignore
				}
			}
		}
	}

	private String readTemplateFileContent() {
		File testTemplateFile = ResourceHelper.getFileForResource(this, TEST_CODE_TEMPLATE_FILE_NAME, "test_code_template_", "vm");
		try {
			return FileUtils.readFileToString(testTemplateFile);
		} catch (Exception e) {
			throw new RuntimeException("Failed to read the test template file " + testTemplateFile.getAbsolutePath(), e);
		}
	}

	@NotNull
	private VelocityContext createVelocityContext() {
		VelocityContext velocityContext = new VelocityContext();
		velocityContext.put("TestActivityName", myLaunchedActivityName);
		velocityContext.put("ClassName", className);
		velocityContext.put("TestMethodName", testMethodName);
		velocityContext.put("PackageName", packageName);
		velocityContext.put("EspressoPackageName", ESPRESSO_STANDARD_PACKAGE);
		velocityContext.put("ResourcePackageName", resourcePackageName);

		// Generate test code.
		ArrayList<String> testCodeLines = new ArrayList<String>();
		for (String event : events) {
			testCodeLines.add(event);
			testCodeLines.add("");
		}

		velocityContext.put("AddContribImport", true);
		velocityContext.put("AddChildAtPositionMethod", true);
		velocityContext.put("TestCode", testCodeLines);

		return velocityContext;
	}

}
