package app.test.migrator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import app.test.migrator.code.generation.TestCodeGenerator;

public class TestCodeGenerationRunner {
	public static void main(String[] args) throws Exception{
		String targetAppDirectory = args[0];		
		String myLaunchedActivityName = args[1];
		String packageName = args[2];
		String resourcePackageName = args[3];
		String scenarioName = args[4];
		String adbPath = args[5];
		String generationType = args[6];

		String userDir = System.getProperty("user.dir"); 

		List<String> events = new ArrayList<String>();
		if (generationType.equals("EventMatching")) {
			StringBuilder previousScenarioName = new StringBuilder();
			String[] scenarioNameSplitted = scenarioName.split("_");
			if (scenarioNameSplitted.length > 1) {
				int scenarioNamePostfix = Integer.parseInt(scenarioNameSplitted[scenarioNameSplitted.length - 1]);
				if (scenarioNamePostfix > 1) {
					for (int i = 0; i < scenarioNameSplitted.length - 1; i++) {
						if (i != scenarioNameSplitted.length - 2)   previousScenarioName.append(scenarioNameSplitted[i]).append(" ");
						else previousScenarioName.append(scenarioNameSplitted[i]).append("_");
						scenarioNamePostfix--;
						previousScenarioName.append(scenarioNamePostfix);
					}
				}
			}

			File prevScenarioFile = new File(userDir + "/target-tests/AppTestMigrator_" + previousScenarioName + ".txt");
			if(previousScenarioName.length() > 0 && prevScenarioFile.exists()) {
				ArrayList<String> prevScenarioGUIEvents = getGUIEvents(prevScenarioFile, true);
				//prevScenarioGUIEvents.remove(0);
				events.addAll(prevScenarioGUIEvents);
			}
		}

		List<String> currentScenarioGUIEvents = new ArrayList<String>();
		File appScenarioFile = new File(userDir + "/target-tests/AppTestMigrator_" + scenarioName + ".txt");
		if(events.size() > 0)	currentScenarioGUIEvents = getGUIEvents(appScenarioFile, false);
		else currentScenarioGUIEvents = getGUIEvents(appScenarioFile, true);
		//if (currentScenarioGUIEvents.size() > 0)	currentScenarioGUIEvents.remove(0);

		events.addAll(currentScenarioGUIEvents);

		if(events.size() > 0) {
			String fileName = "AppTestMigrator_" + scenarioName;
			String className = fileName;
			String testMethodName = fileName.toLowerCase();
			TestCodeGenerator testCodeGenerator = new TestCodeGenerator(targetAppDirectory, myLaunchedActivityName, className, testMethodName, packageName, resourcePackageName, events);
			testCodeGenerator.generate();

			PrintWriter out = new PrintWriter (new FileWriter(appScenarioFile, false));
			for (String event:events) {
				out.write("\n" + event + "~RANDOM");
			}
			if (out != null)	out.close();
			executeCommand(adbPath, "push", userDir + "/target-tests/AppTestMigrator_" + scenarioName + ".txt" , "/sdcard/target-tests/");
		}
	}

	private static void executeCommand(String arg1, String arg2, String arg3, String arg4) {
		String[] cmd = { arg1, arg2, arg3, arg4 };
		ProcessBuilder ps = new ProcessBuilder(cmd).inheritIO();
		ps.redirectErrorStream(true);

		try {
			Process p = ps.start();
			p.waitFor();
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private static ArrayList<String> getGUIEvents(File appScenarioFile, Boolean sleep) throws IOException {
		String appScenario = readFile(appScenarioFile.getAbsolutePath(), StandardCharsets.UTF_8);
		String[] appScenarioSplitted = appScenario.split("\\n");

		List<Integer> indexOfScenarios = new ArrayList<Integer>();
		int count = 0;
		for(int i = 0; i < appScenarioSplitted.length; i++){
			String scenario = appScenarioSplitted[i];
			if(scenario.startsWith("scenario")){
				if(count != 0)	indexOfScenarios.add(i);

				count++;
			}
		}

		indexOfScenarios.add(appScenarioSplitted.length - 1);

		Queue<String> scenarioNames = new LinkedList<String>();
		for(int i = 0; i < appScenarioSplitted.length; i++){
			String scenario = appScenarioSplitted[i];
			if(scenario.startsWith("scenario")){
				scenarioNames.add(scenario.replace("scenario", ""));
			}
		}


		ArrayList<String> events = new ArrayList<String>();
		for(int i = 0; i < indexOfScenarios.size(); i++){
			int index = indexOfScenarios.get(i);
			String[] scenario = Arrays.copyOfRange(appScenarioSplitted, 0, index + 1);	

			List<Integer> nonRandomIndexes = new ArrayList<Integer>();
			for(int j = 0; j < scenario.length; j++){
				if(scenario[j].contains("~RANDOM")){
					nonRandomIndexes.add(j);
				}
			}

			int lastIndexToKeep = -1;

			if(nonRandomIndexes.size() == 1){
				lastIndexToKeep = nonRandomIndexes.get(0);
			} else {	
				for(int j = 0; j < nonRandomIndexes.size() - 1; j++){
					lastIndexToKeep = nonRandomIndexes.get(j + 1);
				}
			}
			scenario = Arrays.copyOfRange(scenario, 0, lastIndexToKeep + 1);

			for(int j = 0; j < scenario.length; j++){
				if(scenario[j] != null && !scenario[j].equals("") && !scenario[j].startsWith("scenario") && (sleep || (!sleep && !scenario[j].equals("try{ Thread.sleep(2500); } catch (InterruptedException e) { }~RANDOM"))))	
					events.add(scenario[j].replaceAll("~RANDOM","").replaceAll("RANDOM", "").replaceAll("null", ""));
			}
		}
		return events;
	}

	private static String readFile(String path, Charset encoding) throws IOException{
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return new String(encoded, encoding);
	}

}
