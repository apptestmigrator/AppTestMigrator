package app.test.migrator.scenario.extraction;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.io.FileUtils;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.imports.ImportDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.rits.cloning.Cloner;

import app.test.migrator.uiautomator.UiHierarchyXmlLoader;
import app.test.migrator.uiautomator.UiNode;
import app.test.migrator.util.AppSetup;
import app.test.migrator.util.Assertion;
import app.test.migrator.util.Event;
import app.test.migrator.util.FiniteStateMachine;
import app.test.migrator.util.GradleParser;
import app.test.migrator.util.ModuleNameExtractor;
import app.test.migrator.util.State;
import app.test.migrator.util.StateAbstraction;
import app.test.migrator.util.Transition;
import app.test.migrator.util.UINode;

public class ExtractScenarios {

	private static final String SCREENSHOTS_DIRECTORY = "/screenshots";
	private static final String UIHIERARCHIES_DIRECTORY = "/UIAutomator-UIHierarchies";
	private static final String ESPRESSO_DIRECTORY = "/Espresso-UIHierarchies";
	private static boolean fileChanged = false;
	private static List<File> espressoFilesWithInstrumentation = new ArrayList<File>();

	public void runInstrumentedSourceApp(String appDirectory, String gradlePath, String adbPath) throws Exception {
		String userDir = System.getProperty("user.dir");

		ModuleNameExtractor moduleNameExtractor = new ModuleNameExtractor();
		String moduleName = moduleNameExtractor.getModuleName(appDirectory);
		if (moduleName == null)
			throw new Exception("The app structure does not satisfy our requirements!");

		GradleParser gradleParser = new GradleParser();
		File gradleFile = gradleParser.parseAndChangeGradle(appDirectory, userDir, moduleName, true, true, true, true, true, false);

		AppSetup appSetup = new AppSetup();
		appSetup.prepareAndroidManifest(appDirectory, userDir, moduleName);

		instrument(userDir, appDirectory);

		// run the test
		executeCommand(appDirectory + "/gradlew", "-p", appDirectory, "connectedAndroidTest");

		// pull screenshots
		executeCommand(adbPath, "pull", "/sdcard/screenshots/", userDir + SCREENSHOTS_DIRECTORY);

		// pull UIAutomator hierarchies
		executeCommand(adbPath, "pull", "/sdcard/UIAutomator-UIHierarchies/", userDir + UIHIERARCHIES_DIRECTORY);

		// pull Espresoo hierarchies
		executeCommand(adbPath, "pull", "/sdcard/Espresso-UIHierarchies/", userDir + ESPRESSO_DIRECTORY);

		// pull log file
		executeCommand(adbPath, "pull", "/sdcard/log", userDir + "/log");

		uninstrument(userDir, appDirectory);

		appSetup.cleanApp(appDirectory, userDir, moduleName, gradleFile, true);

		ImageTranslator imageTranslator = new ImageTranslator(userDir, appDirectory, moduleName,
				userDir + "/UIAutomator-UIHierarchies", false);
		imageTranslator.translateImages();

		parseDynamicLogs(userDir);

		executeCommand(adbPath, "shell", "rm -r", "/sdcard/UIAutomator-UIHierarchies/");
		executeCommand(adbPath, "push", userDir + UIHIERARCHIES_DIRECTORY, "/sdcard/UIAutomator-UIHierarchies/");
	}

	private void executeCommand(String arg1, String arg2, String arg3, String arg4) {
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

	@SuppressWarnings("unchecked")
	private void instrument(String userDir, String appDirectory) throws FileNotFoundException, IOException {
		Collection<File> appDirectoryFiles = FileUtils.listFiles(new File(appDirectory), new String[] { "java" }, true);

		int counter = 0;
		for (File appDirectoryFile : appDirectoryFiles) {
			counter++;
			if (appDirectoryFile.getAbsolutePath().toLowerCase().contains("/src/androidtest")) {
				CompilationUnit cu = JavaParser.parse(appDirectoryFile, Charset.forName("UTF-8"));

				NodeList<ImportDeclaration> imports = cu.getImports();
				fileChanged = false;

				MethodVisitor mv = new MethodVisitor();
				mv.setCounter(counter);
				mv.visit(cu, null);

				if (fileChanged) {
					addImports(imports);
					FileUtils.copyFileToDirectory(appDirectoryFile, new File(userDir + "/resources/"));
					Files.write(appDirectoryFile.toPath(), cu.toString().getBytes());
					espressoFilesWithInstrumentation.add(appDirectoryFile);
				}
			}
		}
	}

	private static void addImports(NodeList<ImportDeclaration> imports) {
		NodeList<ImportDeclaration> importsToRemove = new NodeList<ImportDeclaration>();
		for (ImportDeclaration im : imports) {
			if (im.toString().contains("com.checkdroid.crema.EspressoPlus"))
				importsToRemove.add(im);
		}

		for (ImportDeclaration importToRemove : importsToRemove) {
			imports.remove(importToRemove);
		}

		ImportDeclaration fileWriterImport = JavaParser.parseImport("import java.io.FileWriter;");
		ImportDeclaration printWriterImport = JavaParser.parseImport("import java.io.PrintWriter;");
		ImportDeclaration ioexceptionImport = JavaParser.parseImport("import java.io.IOException;");
		ImportDeclaration environmentImport = JavaParser.parseImport("import android.os.Environment;");

		// add comments so that we know which imports to remove during uninstrumentation
		fileWriterImport.setLineComment("Added");
		printWriterImport.setLineComment("Added");
		ioexceptionImport.setLineComment("Added");
		environmentImport.setLineComment("Added");

		if (!imports.contains(fileWriterImport))
			imports.add(fileWriterImport);
		if (!imports.contains(printWriterImport))
			imports.add(printWriterImport);
		if (!imports.contains(ioexceptionImport))
			imports.add(ioexceptionImport);
		if (!imports.contains(environmentImport))
			imports.add(environmentImport);
	}

	private class MethodVisitor extends VoidVisitorAdapter {
		private int counter;

		@Override
		public void visit(MethodDeclaration m, Object arg) {
			if (m.getAnnotations().size() > 0) {
				for (AnnotationExpr annotation : m.getAnnotations()) {
					if (annotation.toString() != null && annotation.toString().trim().equals("@Test")) {
						addInstrumentationStatements(m);
					}
				}
			} else if (m.getNameAsString().toLowerCase().contains("test")) {
				addInstrumentationStatements(m);
			}
		}

		private void addInstrumentationStatements(MethodDeclaration m) {
			String methodName = m.getNameAsString();
			BlockStmt blockStmt = m.getBody().get();
			NodeList<Statement> stmts = blockStmt.getStmts();

			Statement printWriterDeclStatement = JavaParser.parseStatement("PrintWriter out = null;");
			Statement printWriterStatement = JavaParser.parseStatement(
					"try { out = new PrintWriter(new FileWriter(Environment.getExternalStorageDirectory().getAbsolutePath() + \"/log\""
							+ ", true)); } catch (IOException e) { System.out.println(e.getMessage()); }");
			Statement startInstrStatement = JavaParser
					.parseStatement("out.println(\"START\" + " + "\" \" + \"" + methodName + "\");");
			Statement printWriterFlushStatement = JavaParser.parseStatement("out.flush();");
			Statement endInstrStatement = JavaParser.parseStatement("out.println(\"END\");");
			Statement printWriterCloseStatement = JavaParser.parseStatement("out.close();");

			blockStmt.addStatement(0, printWriterFlushStatement);
			blockStmt.addStatement(0, startInstrStatement);
			blockStmt.addStatement(0, printWriterStatement);
			blockStmt.addStatement(0, printWriterDeclStatement);
			blockStmt.addStatement(stmts.size(), endInstrStatement);
			blockStmt.addStatement(stmts.size(), printWriterCloseStatement);

			fileChanged = true;
		}

		private void setCounter(int counter) {
			this.counter = counter;
		}
	}

	private static void uninstrument(String userDir, String appDirectory) throws IOException {
		for (File espressoFileWithInstrumentation : espressoFilesWithInstrumentation) {
			/*
			 * CompilationUnit cu =
			 * JavaParser.parse(espressoFileWithInstrumentation);
			 * 
			 * new MyVisitor().visit(cu, null);
			 * 
			 * Files.write(espressoFileWithInstrumentation.toPath(),
			 * cu.toString().getBytes());
			 */

			File originalSourceFile = new File(userDir + "/resources/" + espressoFileWithInstrumentation.getName());
			FileUtils.copyFile(originalSourceFile, espressoFileWithInstrumentation);
			originalSourceFile.delete();
		}
	}
	
	private List<FiniteStateMachine> parseDynamicLogs(String projectDir) throws FileNotFoundException, IOException {
		UiHierarchyXmlLoader xmlLoader = new UiHierarchyXmlLoader();
		List<FiniteStateMachine> fsms = new ArrayList<FiniteStateMachine>();

		boolean assertion = false, start = false, onData = false;
		List<FiniteStateMachine> local_fsms = new ArrayList<FiniteStateMachine>();
		List<String> matchers = new ArrayList<String>();
		FiniteStateMachine fsm = new FiniteStateMachine();
		UINode uiNodeFrom = null;
		UINode uiNodeTo = null;
		String action = null;
		UiNode rootFrom = null;
		UiNode rootTo = null;
		String fileNameFrom = null;
		String fileNameTo = null;

		int scenario_counter = 0, BUFFER_SIZE = 1000;
		String method_name = "";
		File logFile = new File(projectDir + "/log");
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(logFile), "UTF8"))) {
			for (String line; (line = br.readLine()) != null;) {
				boolean multiLineAction = false;
				br.mark(BUFFER_SIZE);
				String nextLine = br.readLine();
				if (line.startsWith("START")) {
					method_name = line.split(" ")[1];
					scenario_counter = 0;
					local_fsms = new ArrayList<FiniteStateMachine>();
					start = true;
				}

				if (!start || (line.startsWith("ASSERTION") && nextLine.startsWith("ASSERTION"))) {
					br.reset();

					continue;
				}

				if (nextLine != null && !nextLine.startsWith("START") && !nextLine.startsWith("END")
						&& !nextLine.startsWith("ASSERTION") && !nextLine.startsWith("VIEW")
						&& !nextLine.startsWith("ACTION") && !nextLine.startsWith("MATCHER")
						&& !nextLine.startsWith("onView") && !nextLine.startsWith("onData"))
					multiLineAction = true;

				if (((line.equals("END")) || (line.startsWith("START"))) && (fsm.getStates().size() > 0 && !assertion)) {
					assertion = false;

					Cloner cloner = new Cloner();
					FiniteStateMachine fsmClone = cloner.deepClone(fsm);
					if (!fsms.contains(fsmClone)) {
						fsms.add(fsmClone);
						local_fsms.add(fsmClone);
					}

					if (line.equals("END")) {
						scenario_counter++;
						transformFSMToDOT(fsmClone, false, method_name, scenario_counter, projectDir, null);
						local_fsms = new ArrayList<FiniteStateMachine>();
						start = false;
					}

					fsm = new FiniteStateMachine();
					uiNodeFrom = null;
					uiNodeTo = null;
					action = null;
					rootFrom = null;
					rootTo = null;
					fileNameFrom = null;
					fileNameTo = null;
				} else if (line.startsWith("ASSERTION")) {
					Cloner cloner = new Cloner();
					FiniteStateMachine fsmClone = cloner.deepClone(fsm);
					if (!fsms.contains(fsmClone)) {
						fsms.add(fsmClone);
						local_fsms.add(fsmClone);
					}

					if (fsm.getStates().size() > 0)	scenario_counter++;

					transformFSMToDOT(fsmClone, false, method_name, scenario_counter, projectDir,
							generateAssertion(matchers, line));

					local_fsms = new ArrayList<FiniteStateMachine>();

					if (fsm.getStates().size() > 0)	{ 
						fsm.getStates().remove(fsm.getStates().size() - 1);
						fsm.getTransitions().remove(fsm.getTransitions().size() - 1);
					}

					assertion = true;

					fsm = new FiniteStateMachine();
					uiNodeTo = uiNodeFrom;
					action = null;
					rootFrom = rootTo;
					rootTo = null;
					fileNameFrom = fileNameTo;
					fileNameTo = null;
					matchers = new ArrayList<String>();
				} else if (line.startsWith("onView") && (!onData || matchers.size() < 1)) {
					matchers = new ArrayList<String>();
					if (onData && matchers.size() < 1)	onData = false;
				} else if (line.startsWith("onData")) {
					matchers = new ArrayList<String>();
					onData = true;
				} else if (line.startsWith("MATCHER") && (!onData || matchers.size() < 1 || line.contains("MATCHER matches"))) {
					matchers.add(line.replace("MATCHER ", ""));
				} else {
					assertion = false;

					String[] splittedBySpace = line.split(" ");
					if (splittedBySpace == null
							|| splittedBySpace.length < 1 /* || count == 1 */)
						continue;

					if (line.startsWith("VIEW")) {
						String id = splittedBySpace[1];
						int top = Integer.parseInt(splittedBySpace[2]);
						int left = Integer.parseInt(splittedBySpace[3]);
						int right = Integer.parseInt(splittedBySpace[4]);
						int bottom = Integer.parseInt(splittedBySpace[5]);
						String suffix = "";
						if (splittedBySpace.length > 6)
							suffix = splittedBySpace[6];

						if (fileNameFrom == null && rootFrom == null) {
							fileNameFrom = id + " " + top + " " + left + " " + right + " " + bottom;
							if (!suffix.equals(""))
								fileNameFrom += " " + suffix;
							rootFrom = (UiNode) xmlLoader
									.parseXml(projectDir + UIHIERARCHIES_DIRECTORY + "/" + fileNameFrom + ".xml");
							if (rootFrom != null && rootFrom.getChildCount() > 2)
								rootFrom = (UiNode) rootFrom.getChildren()[2];
							//In case the hierarchy was not captured at the first attempt, try again
							if (rootFrom == null) {
								File f = new File(projectDir + UIHIERARCHIES_DIRECTORY + "/" + fileNameFrom + " 1.xml");
								if (f.exists()) {
									rootFrom = (UiNode) xmlLoader.parseXml(
											projectDir + UIHIERARCHIES_DIRECTORY + "/" + fileNameFrom + " 1.xml");
									if (rootFrom != null && rootFrom.getChildCount() > 2)
										rootFrom = (UiNode) rootFrom.getChildren()[2];

								}
							}
							uiNodeFrom = new UINode(id, top, left, right, bottom);
						} else {
							fileNameTo = id + " " + top + " " + left + " " + right + " " + bottom;
							if (!suffix.equals(""))
								fileNameTo += " " + suffix;
							rootTo = (UiNode) xmlLoader
									.parseXml(projectDir + UIHIERARCHIES_DIRECTORY + "/" + fileNameTo + ".xml");
							if (rootTo != null && rootTo.getChildCount() > 2)
								rootTo = (UiNode) rootTo.getChildren()[2];
							uiNodeTo = new UINode(id, top, left, right, bottom);
						}
					} else if (line.startsWith("ACTION")) {
						StringBuilder actionSB = new StringBuilder();
						for (int i = 1; i < splittedBySpace.length; i++) {
							if (i == 1)
								actionSB.append(splittedBySpace[i]);
							else
								actionSB.append(" " + splittedBySpace[i]);
						}
						if (multiLineAction) {
							actionSB.append(" newline " + nextLine);
						}
						action = actionSB.toString();
					}
				}

				if (rootFrom != null && rootTo != null && action != null) {
					StateAbstraction abs = new StateAbstraction();
					State from = new State(rootFrom, abs.computeFeatureVector(rootFrom), fileNameFrom);
					State to = new State(rootTo, abs.computeFeatureVector(rootTo), fileNameTo);

					fsm.addState(from);
					fsm.addState(to);
					fsm.addTransition(from, to, new Event(action, uiNodeFrom));

					uiNodeFrom = uiNodeTo;
					action = null;
					rootFrom = rootTo;
					rootTo = null;
					fileNameFrom = fileNameTo;
					fileNameTo = null;
				} else if (rootFrom != null && action != null && rootTo == null && nextLine != null
						&& (nextLine.startsWith("ASSERTION") || nextLine.equals("END"))) {
					StateAbstraction abs = new StateAbstraction();
					State from = new State(rootFrom, abs.computeFeatureVector(rootFrom), fileNameFrom);
					State to = new State(null, new Hashtable<String, Integer>(), "END");

					fsm.addState(from);
					fsm.addState(to);
					fsm.addTransition(from, to, new Event(action, uiNodeFrom));
				}

				if (!multiLineAction)
					br.reset();
			}

			if (fsm.getStates().size() > 0) {
				fsms.add(fsm);
				local_fsms.add(fsm);
			}
		}

		return fsms;
	}

	private Assertion generateAssertion(List<String> matchers, String line) {
		if (matchers.size() > 0) {
			String targetElement = null, assertionMethod = null;
			List<String> assertionMatchers = new ArrayList<String>();

			targetElement = line.replace("ASSERTION ", "");

			Boolean skipFirstMatcher = true, skipLastMatcher = true;
			String lastMatcher = matchers.get(matchers.size() - 1).replace("MATCHER ", "");
			if (lastMatcher.equals("matches")) {
				assertionMethod = "matches";
				skipFirstMatcher = false;
			} else if (lastMatcher.equals("doesNotExist")) {
				assertionMethod = "doesNotExist";
				skipFirstMatcher = false;
			} else {
				skipLastMatcher = false;
				String firstMatcher = matchers.get(0).replace("MATCHER ", "");
				if (firstMatcher.equals("isLeftOf"))
					assertionMethod = "isLeftOf";
				else if (firstMatcher.equals("isRightOf"))
					assertionMethod = "isRightOf";
				else if (firstMatcher.equals("isLeftAlignedWith"))
					assertionMethod = "isLeftAlignedWith";
				else if (firstMatcher.equals("isRightAlignedWith"))
					assertionMethod = "isRightAlignedWith";
				else if (firstMatcher.equals("isAbove"))
					assertionMethod = "isAbove";
				else if (firstMatcher.equals("isBelow"))
					assertionMethod = "isBelow";
				else if (firstMatcher.equals("isBottomAlignedWith"))
					assertionMethod = "isBottomAlignedWith";
				else if (firstMatcher.equals("isTopAlignedWith"))
					assertionMethod = "isTopAlignedWith";
				else
					skipFirstMatcher = false;
			}

			for (int i = 0; i < matchers.size(); i++) {
				if ((i == 0 && skipFirstMatcher) || (i == matchers.size() - 1 && skipLastMatcher))
					continue;
				assertionMatchers.add(matchers.get(i).replace("MATCHER ", ""));
			}

			Assertion assertion = new Assertion(targetElement, assertionMethod, assertionMatchers);
			return assertion;
		}

		return null;
	}

	public void transformFSMToDOT(FiniteStateMachine fsm, Boolean forProcess, String method_name, int scenario_counter,
			String projectDir, Assertion assertion) {
		PrintWriter writer = null;
		HashMap<String, State> statesMap = new HashMap<String, State>();
		HashMap<String, Event> eventsMap = new HashMap<String, Event>();

		String scenarioDir = projectDir + "/source-scenarios/";

		File dir = new File(scenarioDir);
		if (!dir.exists())
			dir.mkdir();

		boolean scenarioExist = false;
		if (fsm.getTransitions().size() > 0)
			scenarioExist = true;

		try {
			writer = new PrintWriter(new FileWriter(scenarioDir + method_name + "_" + scenario_counter, true));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		if (scenarioExist) {
			for (Transition transition : fsm.getTransitions()) {
				StringBuilder sb = new StringBuilder();

				State from = transition.getFrom();

				String fromHash = "";
				if (forProcess) {
					fromHash = from.getFileName().replaceAll("\\s", "");
					statesMap.put(fromHash, from);
				} else
					fromHash = "Transition:" + from.getFileName();

				sb.append(fromHash);

				if (forProcess)	sb.append("->");
				else sb.append(">");

				State to = transition.getTo();

				String toHash = "";
				if (forProcess) {
					toHash = to.getFileName().replaceAll("\\s", "");
					statesMap.put(toHash, to);
				} else
					toHash = to.getFileName();

				sb.append(toHash);

				if (forProcess)	sb.append("[label=");

				Event event = transition.getLabel();

				String eventHash = "";
				if (forProcess) {
					eventHash = event.toString().replaceAll("[^A-Za-z0-9]", "");
					eventsMap.put(eventHash, event);
				} else {
					eventHash = "[" + event.getAction() + "]" + "[" + event.getTargetElement().toString() + "]";
				}

				sb.append(eventHash);

				if (forProcess)	sb.append("];");

				writer.println(sb.toString());
				writer.flush();
			}

			if (assertion != null) {
				recordAssertion(assertion, writer);
			}
		} else if (assertion != null) {
			recordAssertion(assertion, writer);
		}

		if (writer != null)	writer.close();
	}

	private void recordAssertion(Assertion assertion, PrintWriter writer) {
		String targetElement = assertion.getTargetElement();
		String assertionMethod = assertion.getAssertionMethod();
		List<String> matchers = assertion.getMatchers();

		StringBuilder sb = new StringBuilder();
		sb.append("Assertion:[" + targetElement + "]" + "[" + assertionMethod + "]");

		boolean hierarchyAssertion = false, prevHierarchyAssertion = false;
		for (String matcher: matchers) {
			if (matcher.equals("withParent") || matcher.equals("withChild") || matcher.equals("hasDescendant")
					|| matcher.equals("isDescendantOfA") || matcher.equals("hasSibling")) hierarchyAssertion = true;
			else hierarchyAssertion = false;

			if (!prevHierarchyAssertion)	sb.append("[");
			else sb.append("-");
			sb.append(matcher);
			if (!hierarchyAssertion)	sb.append("]");

			prevHierarchyAssertion = hierarchyAssertion;
		}
		writer.println(sb.toString());
	}
}
