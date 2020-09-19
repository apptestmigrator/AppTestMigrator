package app.test.migrator.util;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

public class ModuleNameExtractor {
	
	public String getModuleName(String appDirectory) throws IOException {
		String gradleSettingsFileContent = FileUtils.readFileToString(new File(appDirectory + "/settings.gradle"));
		String[] gradleSettingsFile = gradleSettingsFileContent.split(",");

		for(String file : gradleSettingsFile){
			int colonIndex = file.indexOf(':');
			int singleQuoteIndex = file.indexOf('\'', colonIndex + 1);
			String moduleName = file.substring(colonIndex + 1, singleQuoteIndex);

			File buildGradle = new File(appDirectory + "/" + moduleName + "/" + "build.gradle");
			File java = new File(appDirectory + "/" + moduleName + "/" + "src" + "/" + "main" + "/" + "java");
			File res = new File(appDirectory + "/" + moduleName + "/" + "src" + "/" + "main" + "/" + "res");
			File manifest = new File(appDirectory + "/" + moduleName + "/" + "src" + "/" + "main" + "/" + "AndroidManifest.xml");
			
			if(!buildGradle.exists() || !java.exists() || !res.exists() || !manifest.exists() || moduleName.toLowerCase().contains("library"))	continue;
			else return moduleName;
		}

		return null;
	}
	
}
