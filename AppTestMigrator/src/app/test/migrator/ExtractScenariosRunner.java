package app.test.migrator;

import java.io.FileWriter;
import java.util.HashMap;

import app.test.migrator.scenario.extraction.ExtractScenarios;
import app.test.migrator.scenario.extraction.ImageTranslator;
import app.test.migrator.util.AppSetup;
import app.test.migrator.util.GradleParser;
import app.test.migrator.util.ModuleNameExtractor;

public class ExtractScenariosRunner {
	
	public static void main(String[] args) throws Exception{
		String sourceAppDirectory = args[0];
		String targetAppDirectory = args[1];
		String gradlePath = args[2];
		String adbPath = args[3];
		
		String userDir = System.getProperty("user.dir"); 
		
		ExtractScenarios extractScenarios = new ExtractScenarios();
		extractScenarios.runInstrumentedSourceApp(sourceAppDirectory, gradlePath, adbPath);
		
		ModuleNameExtractor moduleNameExtractor = new ModuleNameExtractor();
		String moduleName = moduleNameExtractor.getModuleName(targetAppDirectory);
		System.out.println("module name=" + moduleName);
		
		GradleParser gradleParser = new GradleParser();
		gradleParser.parseAndChangeGradle(targetAppDirectory, userDir, moduleName, true, true, true, false, true, true);

		AppSetup appSetup = new AppSetup();
		appSetup.prepareAndroidManifest(targetAppDirectory, userDir, moduleName);
		
		ImageTranslator imageTranslator = new ImageTranslator(userDir, targetAppDirectory, moduleName, "", true);
		imageTranslator.translateImages();
		
		HashMap<String, String> id_image = imageTranslator.getIdImage();
		FileWriter fw = new FileWriter(userDir + "/image_dict");
		for(String key : id_image.keySet()){
			fw.write(key + " " + id_image.get(key) + "\n");
		}
		fw.close();

		HashMap<String, String> id_inputType = imageTranslator.getIdInputType();
		fw = new FileWriter(userDir + "/inputType_dict");
		for(String key : id_inputType.keySet()){
			if (!id_inputType.get(key).equals(""))	fw.write(key + " " + id_inputType.get(key) + "\n");
		}
		fw.close();
	}
}
