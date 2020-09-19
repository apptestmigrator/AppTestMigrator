package app.test.migrator.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.GroovyCodeVisitor;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.control.SourceUnit;

public class GradleParser {

	private boolean addGuava = true;
	private boolean addDagger = true;
	private boolean addDaggerCompiler = true;
	private boolean addDaggerProducers = true;
	private boolean addUIAutomator = true;
	private boolean addSupportAnnotations = true;
	private boolean addRunner = true;
	private boolean addRules = true;
	private boolean addExtraLibs = true;
	private boolean enableMultiDex = true;
	private boolean addMultiDex = true;
	private boolean testInstrumentationRunner = true;
	private boolean addHamcrest = true;
	private boolean addSupportDesign = true;
	private boolean addRecyclerview = true;
	private boolean addJavaxAnnotation = true;

	private String MIN_SDK_VERSION = "21";
	private String TARGET_SDK_VERSION = "22";
	private String applicationId;

	public File parseAndChangeGradle(String appDirectory, String userDir, String moduleName, Boolean keepOriginalGradle, Boolean changeTargetSdkVersion, Boolean changeMinSdkVersion, Boolean isSourceApp, Boolean changeGradleDependencies, Boolean extraLibs) throws IOException{
		File gradleFile = new File(appDirectory + "/" + moduleName + "/" + "build.gradle");

		SourceUnit unit = SourceUnit.create("build.gradle", readFile(gradleFile.getAbsolutePath(), StandardCharsets.UTF_8));
		unit.parse();
		unit.completePhase();
		unit.convert();

		ParseGradleFileVisitor parseGradleFileVisitor = new ParseGradleFileVisitor();
		visitScriptCode(unit, parseGradleFileVisitor);

		if(keepOriginalGradle)	FileUtils.copyFileToDirectory(gradleFile, new File(userDir  + "/resources/"));

		if(changeGradleDependencies){
			List<Integer> buildGradleLineNumbersToRemove = parseGradleFileVisitor.getStatementsToRemove();				

			String gradleFileContentWithRemovedLines = removeLines(buildGradleLineNumbersToRemove, gradleFile);
			try (PrintStream out = new PrintStream(new FileOutputStream(gradleFile))) {
				out.print(gradleFileContentWithRemovedLines);
				out.close();
			}
		}

		int dependencyLineNumberToAdd = parseGradleFileVisitor.getDependencyLineNumberToAdd();
		int configLineNumberToAdd = parseGradleFileVisitor.getConfigLineNumberToAdd();

		List<String> lines = Files.readAllLines(Paths.get(gradleFile.getAbsolutePath()), StandardCharsets.UTF_8);

		String targetSDKVersion = parseGradleFileVisitor.getTargetedSDKVersion();
		String minSDKVersion = parseGradleFileVisitor.getMinSDKVersion();

		List<String> newLines = new ArrayList<String>();
		List<Integer> indexesToReplace = new ArrayList<Integer>();

		for(int index = 0; index < lines.size(); index++){
			String line = lines.get(index);

			if(changeTargetSdkVersion && Integer.parseInt(targetSDKVersion) > 22 && line != null && line.contains("targetSdkVersion")){
				String[] lineSpliteedBySpace = line.split(" ");
				if(lineSpliteedBySpace.length > 0){
					newLines.add(line.replace(targetSDKVersion, TARGET_SDK_VERSION));
					indexesToReplace.add(index);
				}
			}

			if(changeMinSdkVersion && Integer.parseInt(minSDKVersion) < 21 && line != null && line.contains("minSdkVersion")){
				String[] lineSpliteedBySpace = line.split(" ");
				if(lineSpliteedBySpace.length > 0){
					newLines.add(line.replace(minSDKVersion, MIN_SDK_VERSION));
					indexesToReplace.add(index);
				}
			}

			if(index  >= dependencyLineNumberToAdd - 1){
				if(line.contains("guava") && !line.contains("//"))	addGuava = false;
				if(line.contains("dagger-compiler") && !line.contains("//"))	addDaggerCompiler = false;
				if(line.contains("dagger-producers") && !line.contains("//"))	addDaggerProducers = false;
				if(line.contains("dagger") && !line.contains("dagger-compiler") && !line.contains("dagger-producers") && !line.contains("//"))	addDagger = false;
				if(line.contains("uiautomator") && !line.contains("//"))	addUIAutomator = false;
				if(line.contains("com.android.support:support-annotations") && !line.contains("//"))	addSupportAnnotations = false;
				if(line.contains("com.android.support:design") && !line.contains("//"))	addSupportDesign = false;
				if(line.contains("com.android.support:recyclerview") && !line.contains("//"))	addRecyclerview = false;
				if(line.contains("com.android.support.test:runner:0.5") && !line.contains("//"))	addRunner = false;
				if(line.contains("com.android.support.test:rules:0.5") && !line.contains("//"))	addRules = false;
				if(line.contains("compile fileTree(dir: 'libs', include: ['*.jar'])"))	addExtraLibs = false;	
				if(line.contains("javax.annotation"))	addJavaxAnnotation = false;
				if(line.contains("hamcrest-library") && !line.contains("//")){
					if(line.contains("testCompile")){
						newLines.add("");
						indexesToReplace.add(index);
					} else {
						if(line.contains("1.1")){
							newLines.add("");
							indexesToReplace.add(index);
						} else addHamcrest = false;
					}
				}
			}

			if(index  >= configLineNumberToAdd - 1){
				if(line.contains("multiDexEnabled true") && !line.contains("//"))	enableMultiDex = false;
				if(line.contains("com.android.support:multidex") && !line.contains("//"))	addMultiDex = false;
				if(line.contains("testInstrumentationRunner") && !line.contains("//")){
					if(!line.contains("android.support.test.runner.AndroidJUnitRunner")){
						newLines.add("        testInstrumentationRunner \"android.support.test.runner.AndroidJUnitRunner\"");
						indexesToReplace.add(index);
					}
					testInstrumentationRunner = false;
				}
			}
		}

		for(int i = 0; i < newLines.size(); i++){
			lines.set(indexesToReplace.get(i), newLines.get(i));
		}

		if(enableMultiDex){
			lines.add(configLineNumberToAdd, "        multiDexEnabled true");

			dependencyLineNumberToAdd ++;
		}

		if(testInstrumentationRunner){
			lines.add(configLineNumberToAdd, "        testInstrumentationRunner \"android.support.test.runner.AndroidJUnitRunner\"");
		}
	
		if(changeGradleDependencies){
			try(BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream("./resources/build.gradle-template"), "UTF8"))) {
				for(String line; (line = br.readLine()) != null; ) {
					if(line.indexOf('?') > 0)	line = line.replaceAll("\\?", parseGradleFileVisitor.getAppVersion());

					if(line.contains("provided files('libs/guava-18.0.jar')") && !addGuava)	continue;
					if(line.contains("provided files('libs/dagger-compiler-2.1-20150513.195817-5.jar')") && !addDaggerCompiler)	continue;
					if(line.contains("provided files('libs/dagger-producers-2.1-20150513.195750-5.jar')") && !addDaggerProducers)	continue;
					if(line.contains("provided files('libs/dagger-2.1-20150513.195741-5.jar')") && !addDagger)	continue;
					if(line.contains("compile project(\":uiautomator-v18-release\")") && !addUIAutomator)	continue;
					if(line.contains("com.android.support:support-annotations") && !addSupportAnnotations)	continue;
					if(line.contains("com.android.support:design") && !addSupportDesign)	continue;
					if(line.contains("com.android.support:recyclerview") && !addRecyclerview)	continue;
					if(line.contains("com.android.support.test:runner") && !addRunner)	continue;
					if(line.contains("com.android.support.test:rules") && !addRules)	continue;
					if(line.contains("com.android.support:multidex") && !addMultiDex)	continue;
					if(line.contains("org.hamcrest:hamcrest-library")){
						if(!line.contains("testCompile") && !addHamcrest)	continue;
					}
					if(line.contains("javax.annotation:javax.annotation-api") && !addJavaxAnnotation)	continue;

					if(!lines.contains(line))	lines.add(dependencyLineNumberToAdd, line);		
				}

				if(extraLibs && addExtraLibs){
					lines.add(dependencyLineNumberToAdd, "    compile files('libs/edu.mit.jwi_2.4.0_jdk.jar')");
					lines.add(dependencyLineNumberToAdd, "    compile files('libs/stanford-corenlp-3.4.1.jar')");
					lines.add(dependencyLineNumberToAdd, "    compile files('libs/stanford-corenlp-3.4.1-models.jar')");
					lines.add(dependencyLineNumberToAdd, "    compile files('libs/opencv-2.4.13-0.jar')");
					lines.add(dependencyLineNumberToAdd, "    compile files('libs/org.apache.commons.io.jar')");
				}
			}

			File libsDirectory = new File(appDirectory + "/" + moduleName + "/libs");
			if (!libsDirectory.exists())	libsDirectory.mkdir();

			//if(addDagger)	FileUtils.copyFileToDirectory(new File("./resources/dagger-2.1-20150513.195741-5.jar"), libsDirectory);
			//if(addDaggerCompiler)	FileUtils.copyFileToDirectory(new File("./resources/dagger-compiler-2.1-20150513.195817-5.jar"), libsDirectory);
			//if(addDaggerProducers)	FileUtils.copyFileToDirectory(new File("./resources/dagger-producers-2.1-20150513.195750-5.jar"), libsDirectory);
			//if(addGuava)	FileUtils.copyFileToDirectory(new File("./resources/guava-18.0.jar"), libsDirectory);

			File settingFile = new File(appDirectory + "/settings.gradle");
			String gradleSettingsFileContent = FileUtils.readFileToString(settingFile);

			if(!gradleSettingsFileContent.contains("espresso")){
				if(addUIAutomator)	gradleSettingsFileContent = gradleSettingsFileContent.trim() + ",':espresso-contrib-release', ':espresso-core-release', ':espresso-idling-resource-release', ':espresso-intents-release', ':uiautomator-v18-release'";
				else gradleSettingsFileContent = gradleSettingsFileContent.trim() + ",':espresso-contrib-release', ':espresso-core-release', ':espresso-idling-resource-release', ':espresso-intents-release'";
			}

			FileUtils.writeStringToFile(settingFile, gradleSettingsFileContent);

			FileUtils.copyDirectoryToDirectory(new File("./resources/espresso-contrib-release"), new File(appDirectory));
			if(isSourceApp)	FileUtils.copyDirectoryToDirectory(new File("./resources/source/espresso-core-release"), new File(appDirectory));
			else	FileUtils.copyDirectoryToDirectory(new File("./resources/target/espresso-core-release"), new File(appDirectory));
			FileUtils.copyDirectoryToDirectory(new File("./resources/espresso-idling-resource-release"), new File(appDirectory));
			FileUtils.copyDirectoryToDirectory(new File("./resources/espresso-intents-release"), new File(appDirectory));
			if(addUIAutomator)	FileUtils.copyDirectoryToDirectory(new File("./resources/uiautomator-v18-release"), new File(appDirectory));		
		}

		Files.write(Paths.get(gradleFile.getAbsolutePath()), lines, StandardCharsets.UTF_8);

		applicationId = parseGradleFileVisitor.getApplicationId();

		return gradleFile;
	}

	private class ParseGradleFileVisitor extends CodeVisitorSupport{		

		private List<Integer> statementsToRemove = new ArrayList<Integer>();
		private int dependencyLineNumberToAdd = 0, configLineNumberToAdd = 0;
		private String targetedSDKVersion = "", minSDKVersion = "", applicationId = "";
		private Set<String> appVersions = new TreeSet<String>();

		@Override
		public void visitExpressionStatement(ExpressionStatement statement){
			super.visitExpressionStatement(statement);

			String expressionText = statement.getExpression().getText();

			if((expressionText.contains("androidTestCompile") || expressionText.contains("compile")) && (expressionText.contains("espresso") || expressionText.contains("checkdroid:crema") || (expressionText.contains("com.android.support.test:runner") && !expressionText.contains("0.5")) || (expressionText.contains("com.android.support.test:rules") && !expressionText.contains("0.5")))){
				int firstLine = statement.getExpression().getLineNumber();
				int lastLine = statement.getExpression().getLastLineNumber();

				if(firstLine == lastLine){
					statementsToRemove.add(firstLine);
				} else {
					while(lastLine - firstLine >= 0){
						statementsToRemove.add(firstLine);
						firstLine++;
					}
				}
			}

			if(expressionText.startsWith("this.dependencies(")){
				dependencyLineNumberToAdd = statement.getExpression().getLineNumber() + 1;
			}

			if(expressionText.startsWith("this.defaultConfig(")){
				configLineNumberToAdd = statement.getExpression().getLineNumber() + 1;
			}

			if(expressionText.startsWith("this.compile(")){
				String lib = expressionText.substring(expressionText.indexOf('(') + 1, expressionText.indexOf(')'));
				String[] libSplited = lib.split(":");
				if(libSplited.length > 0){
					String version = libSplited[libSplited.length - 1];
					Pattern pattern = Pattern.compile("^\\d+(\\.\\d+)+\\-?[a-zA-Z0-9]*$");
					Matcher matcher = pattern.matcher(version);

					if((matcher.find() || version.contains("+")) && !version.startsWith("0"))	appVersions.add(version);
				}
			}

			if(expressionText.startsWith("this.targetSdkVersion(")){
				targetedSDKVersion = expressionText.substring(expressionText.indexOf('(') + 1, expressionText.indexOf(')'));
			}

			if(expressionText.startsWith("this.minSdkVersion(")){
				minSDKVersion = expressionText.substring(expressionText.indexOf('(') + 1, expressionText.indexOf(')'));
			}

			if(expressionText.startsWith("this.applicationId(")){
				applicationId = expressionText.substring(expressionText.indexOf('(') + 1, expressionText.indexOf(')'));
			}	
		}

		public List<Integer> getStatementsToRemove(){   return statementsToRemove;	}

		public int getDependencyLineNumberToAdd(){   return dependencyLineNumberToAdd;	}

		public int getConfigLineNumberToAdd(){   return configLineNumberToAdd;	}

		public String getAppVersion(){ 
			String[] appVersionsArray = (String[])appVersions.toArray(new String[0]);
			Arrays.sort(appVersionsArray, new VersionComparator());
			if(appVersions.size() > 0){
				String appVersion = appVersionsArray[0];
				if(appVersion.contains("-")){
					int index = appVersion.indexOf("-");
					appVersion = appVersion.substring(0, index);
				}
				return appVersion;
			} else return "25.0.0";
		}

		public String getTargetedSDKVersion(){   return targetedSDKVersion;	}

		public String getMinSDKVersion(){   return minSDKVersion;	}

		public String getApplicationId(){   return applicationId;	}
	}

	private class VersionComparator implements Comparator<String>  {

		public int compare(String version1, String version2) {
			String parts1[] = getVersionParts(version1),
				   parts2[] = getVersionParts(version2);
			for (int i = 0 ; i < Math.min(parts1.length, parts2.length); i++) {
				if(parts1[i].contains("-")){
					int index = parts1[i].indexOf("-");
					parts1[i] = parts1[i].substring(0, index);
				}

				if(parts2[i].contains("-")){
					int index = parts2[i].indexOf("-");
					parts2[i] = parts2[i].substring(0, index);
				}

				int partComparison = compareVersionPart(parts1[i], parts2[i]);
				if (partComparison != 0){
					return partComparison;
				}
			}

			if (parts1.length > parts2.length) {
				return -1;
			} else if (parts1.length < parts2.length) {
				return 1;
			} else {
				return 0;
			}
		}

		protected String[] getVersionParts(String version) {
			return version.split("\\.");
		}

		protected int compareVersionPart(String part1, String part2) {
			int versionPart1, versionPart2;
			try {
				versionPart1 = Integer.parseInt(part1);
				versionPart2 = Integer.parseInt(part2);
			} catch (NumberFormatException e) {
				return 0;
			}

			if (versionPart1 > versionPart2) {
				return -1;
			} else if (versionPart1 < versionPart2) {
				return 1;
			} else {
				return 0;
			}
		}
	}

	private String readFile(String path, Charset encoding) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return new String(encoded, encoding);
	}

	private void visitScriptCode(SourceUnit source, GroovyCodeVisitor transformer) {
		source.getAST().getStatementBlock().visit(transformer);
		for (Object method : source.getAST().getMethods()) {
			MethodNode methodNode = (MethodNode) method;
			methodNode.getCode().visit(transformer);
		}
	}

	private String removeLines(List<Integer> removeLines, File text) throws IOException {
		StringBuilder builder = new StringBuilder();
		LineIterator it = FileUtils.lineIterator(text);

		try {
			for (int i = 1; it.hasNext(); i++) {
				String line = it.nextLine();

				if(!removeLines.contains(i)) {
					builder.append(line).append(System.lineSeparator());
				}
			}
		} finally {
			LineIterator.closeQuietly(it);
		}

		return builder.toString();   
	}

	public String getApplicationId(){	return applicationId;	}
}
