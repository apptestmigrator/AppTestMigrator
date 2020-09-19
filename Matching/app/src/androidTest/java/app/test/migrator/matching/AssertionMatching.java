package app.test.migrator.matching;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.app.Instrumentation;

import android.os.SystemClock;
import android.support.test.uiautomator.UiDevice;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.AmbiguousViewMatcherException;
import android.support.test.espresso.Espresso;
import android.support.test.espresso.NoMatchingViewException;
import android.support.test.espresso.PerformException;
import android.support.test.espresso.intent.Intents;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import android.util.Log;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Collection;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Collections;
import java.util.Comparator;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.File;
import java.io.Writer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.support.test.uiautomator.UiDevice;

import app.test.migrator.matching.util.*;
import app.test.migrator.matching.util.uiautomator.*;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.closeSoftKeyboard;
import static android.support.test.espresso.action.ViewActions.longClick;
import static android.support.test.espresso.action.ViewActions.replaceText;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static android.support.test.espresso.intent.Intents.intending;
import static android.support.test.espresso.intent.matcher.IntentMatchers.isInternal;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withContentDescription;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static android.support.test.espresso.matcher.ViewMatchers.withXPath;
import static android.support.test.runner.lifecycle.Stage.RESUMED;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.not;

import okhttp3.RequestBody;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.Route;

@RunWith(AndroidJUnit4.class)
public class AssertionMatching {
    private Bundle b = InstrumentationRegistry.getArguments();
    private Class cl = determineClass(b);
    private String module = determineModuleName(b);
    private String scenarioName = determineScenarioName(b);
    private String mode = determineMode(b);
    private final String LOG_TAG = InstrumentationRegistry.getTargetContext().getPackageName();
    private boolean done = false, navigationDrawerIsOpen = false ;
    private FiniteStateMachine scenario = new FiniteStateMachine();
    private List<UiNode> nodes = new ArrayList<UiNode>();
    private UiDevice mDevice;
    private Map<String, Double> dictionary = new HashMap<String, Double>();
    private Map<String, String> id_image = new HashMap<String, String>();
    private Map<String, String> id_inputType = new HashMap<String, String>();
    private List<Quintuple<String, State, Event, Integer, String>> lines = new ArrayList<Quintuple<String, State, Event, Integer, String>>();
    private List<String> assertionLines = new ArrayList<String>();
    private Writer out = null;
    private boolean pressBack = false, terminate = false;
    private OkHttpClient client = new OkHttpClient.Builder().authenticator(new Authenticator() {
        public Request authenticate(Route route, Response response) throws IOException {
            String credential = Credentials.basic("neo4j", "neo4j");
            return response.request().newBuilder().header("Authorization", credential).build();
        }
    }).build();
    private LemmatizationAndPOSTagger lemmatizationAndPOSTagger = new LemmatizationAndPOSTagger();
    private int lastVisitedTransitionIndex;
    private List<Assertion> assertions = new ArrayList<Assertion>();

    @Rule
    public ActivityTestRule activityRule = new ActivityTestRule(cl);

    @Before
    public void setUp() throws Exception {
        Intents.init();
        intending(not(isInternal())).respondWith(new Instrumentation.ActivityResult(0, null));

        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        getSourceAppAssertions();

        createIdImageDict("image_dict", true);
        createIdImageDict("inputType_dict", false);

        String path = Environment.getExternalStorageDirectory().getAbsolutePath();
        File dir = new File(path + "/target-tests/");
        if (!dir.exists()) dir.mkdir();
        File scenarioFile = new File(dir, "AppTestMigrator_" + scenarioName + ".txt");
        out = new PrintWriter(new FileWriter(scenarioFile, true));
    }

    private void createIdImageDict(String name, Boolean isImageDict) throws IOException {
        InputStream imageDictInputStream = InstrumentationRegistry.getTargetContext().getResources().getAssets().open(name);

        if (imageDictInputStream != null) {
            BufferedReader br = new BufferedReader(new InputStreamReader(imageDictInputStream, "UTF8"));
            for (String line; (line = br.readLine()) != null; ) {
                String[] lineSplittedBySpace = line.split(" ");
                if (lineSplittedBySpace.length > 1) {
                    if (isImageDict)    id_image.put(lineSplittedBySpace[0], lineSplittedBySpace[1]);
                    else id_inputType.put(lineSplittedBySpace[0], lineSplittedBySpace[1]);
                }
            }
        }
    }

    @After
    public void finish() throws IOException {
        if (!terminate) {
            writeAssertionToFile();
        }
        Intents.release();
    }

    private void writeAssertionToFile() throws IOException {
        int index = 0;
        boolean assertionExist = false;
        for (Quintuple<String, State, Event, Integer, String> line : lines) {
            if (assertionLines.size() == index) break;
            if (line.first.equals("ASSERTION")) {
                String assertionCode = assertionLines.get(index);
                if (assertionCode.length() > 0) {
                    assertionExist = true;
                    out.write(assertionCode + "~RANDOM");
                }
                index++;
            } else out.write(line.first);
        }

        if (assertionExist && out != null) out.close();
    }

    private String generateAssertionCode(Assertion assertion) {
        StringBuilder assertionCode = new StringBuilder();
        String assertionTargetElement = selectAssertionTargetElement(assertion.getTargetElement());
        if (!assertionTargetElement.equals("")) {
            assertionCode.append(assertionTargetElement);
            assertionCode.append(".check");
            assertionCode.append("(");
            assertionCode.append(assertion.getAssertionMethod());
            assertionCode.append("(");
            if (assertion.getMatchers().size() > 1) assertionCode.append("allOf(");
            for (int index = 0; index < assertion.getMatchers().size(); index++) {
                AssertionMatcher assertionMatcher = assertion.getMatchers().get(index);
                if (assertionMatcher.getHierarchyMethod() != null)
                    assertionCode.append(assertionMatcher.getHierarchyMethod()).append("(");
                String method = assertionMatcher.getMethod();
                assertionCode.append(method);
                assertionCode.append("(");
                if (method.equals("withText")) assertionCode.append("\"");
                if (assertionMatcher.getParameter() != null) {
                    String parameterValue = findParameterValue(method, assertionMatcher.getParameter());
                    if (parameterValue.equals(""))  return "";
                    assertionCode.append(parameterValue);
                }
                if (method.equals("withText")) assertionCode.append("\"");
                assertionCode.append(")");
                if (assertionMatcher.getHierarchyMethod() != null) assertionCode.append(")");
                if (index != assertion.getMatchers().size() - 1) assertionCode.append(", ");
            }
            if (assertion.getMatchers().size() > 1) assertionCode.append(")");
            assertionCode.append(")");
            assertionCode.append(")");
            assertionCode.append(";");
        }
        return assertionCode.toString();
    }

    private String findParameterValue(String method, UiNode parameter) {
        String parameterValue = "";
        switch (method) {
            case "withId":
                String resource_id = parameter.getAttribute("resource-id");
                if (resource_id != null && resource_id.contains("/"))
                    resource_id = resource_id.split("/")[1];
                parameterValue = "R.id." + resource_id;
                break;
            case "withText":
                parameterValue = parameter.getAttribute("text");
                break;
            case "withContentDescription":
                parameterValue = parameter.getAttribute("content-desc");
                break;
        }
        return parameterValue;
    }

    @Test
    public void test() {
        if (!mode.equals("AssertionMatching"))  terminate = true;
        else {
            replayCorrespondingScenarioIfExists();

            if (assertions.size() > 0) {
                long startTimeMillis = SystemClock.uptimeMillis(), currTimeMillis, TIMEOUT = 90000;
                FiniteStateMachine fsm = new FiniteStateMachine();
                State currState, prevState = null;
                Triple<Event, Event, Integer> nextAction = new Triple<>(new Event(), new Event(), -1), prevAction = null;

                try {
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                while (!done) {
                    currTimeMillis = SystemClock.uptimeMillis();
                    if (currTimeMillis - startTimeMillis > TIMEOUT) {
                        break;
                    }

                    pressBack = false;

                    UiNode root = getRoot();

                    if (root == null || root.getChildCount() < 1) break;

                    currState = getCurrentState(fsm);
                    List<Pair<Assertion, Event>> matchedAssertions = findSimilarAssertions(currState);

                    for (Pair<Assertion, Event> matchedAssertion : matchedAssertions) {
                        List<AssertionMatcher> assertionMatchers = new ArrayList<AssertionMatcher>();
                        for (AssertionMatcher assertionMatcher : matchedAssertion.first.getMatchers()) {
                            if ((matchedAssertion.second.getTargetElement().getAttribute("class").contains("Image") ||
                                    matchedAssertion.second.getTargetElement().getAttribute("class").contains("Layout")) && assertionMatcher.getMethod().contains("withText")) {
                                break;
                            }
                            if (assertionMatcher.getParameter() != null) {
                                Event matchedNode = findMatchedActionable(currState, assertionMatcher.getParameter());
                                if (matchedNode != null) {
                                    assertionMatcher = new AssertionMatcher(assertionMatcher.getHierarchyMethod(), assertionMatcher.getMethod(), matchedNode.getTargetElement());
                                    assertionMatchers.add(assertionMatcher);
                                }
                            } else {
                                assertionMatcher = new AssertionMatcher(assertionMatcher.getHierarchyMethod(), assertionMatcher.getMethod(), null);
                                assertionMatchers.add(assertionMatcher);
                            }
                        }
                        if (assertionMatchers.size() > 0) {
                            lines.add(new Quintuple<>("ASSERTION", new State(), new Event(), -1, ""));
                            String assertionCode = generateAssertionCode(new Assertion(matchedAssertion.second.getTargetElement(), matchedAssertion.first.getAssertionMethod(), assertionMatchers));
                            assertionLines.add(assertionCode);
                        }
                        matchedAssertion.first.setVisited();
                        assertions.set(assertions.indexOf(matchedAssertion.first), matchedAssertion.first);
                    }

                    boolean allAssertionsMatched = true;
                    for (Assertion assertion : assertions) {
                        if (!assertion.getVisited()) {
                            allAssertionsMatched = false;
                        }
                    }

                    if (allAssertionsMatched) {
                        done = true;
                    } else {
                        if (nextAction.first.getTargetElement() == null) {
                            nextAction = pickRandomAction(currState, nextAction, currState.getActionables(), prevAction);
                        }

                        if (nextAction.first.getTargetElement() == null) {
                            try {
                                prevState = currState;
                                Espresso.pressBack();
                                currState = getCurrentState(fsm);
                                addStateAndTransitionToFSM(fsm, currState, prevState, new Triple<Event, Event, Integer>(new Event("PRESS_BACK", null, "", ""), new Event(), 0));
                                lines.add(new Quintuple<String, State, Event, Integer, String>("\npressBack();RANDOM", getCurrentState(fsm), null, -1, null));
                            } catch (RuntimeException e) {
                                break;
                            }
                            continue;
                        }

                        if (nextAction.first.getTargetElement() != null) {
                            prevState = currState;
                            try {
                                performAction(nextAction, fsm);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            currState = getCurrentState(fsm);
                            addStateAndTransitionToFSM(fsm, currState, prevState, nextAction);
                        }

                        prevAction = nextAction;
                        nextAction = new Triple<>(new Event(), new Event(), -1);
                    }
                }
            }
        }
    }

    private void replayCorrespondingScenarioIfExists() {
        try {
            InputStream previousScenarioInputStream = InstrumentationRegistry.getTargetContext().getResources().getAssets().open("migrated-events/AppTestMigrator_" + scenarioName + ".java");
            if (previousScenarioInputStream != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(previousScenarioInputStream, "UTF8"));
                Boolean skip = true;
                for (String line; (line = br.readLine()) != null;) {
                    if (line.contains("@Test")) skip = false;
                    if (line.contains("@Before")) skip = true;
                    if (!skip) {
                        if (line.contains("pressBack()")) {
                            Espresso.pressBack();
                        } else if (line.contains("onData")) {
                            int position = Integer.parseInt(line.substring(line.indexOf("atPosition(") + 11, line.indexOf(")", line.indexOf("atPosition("))));
                            if (line.contains("withId")) {
                                String id = line.substring(line.indexOf("withId(") + 7, line.indexOf(")", line.indexOf("withId")));
                                int resource_id = InstrumentationRegistry.getTargetContext().getResources().getIdentifier(id.replace("R.id.", ""), "id", InstrumentationRegistry.getTargetContext().getPackageName());
                                onData(anything()).inAdapterView(withId(resource_id)).atPosition(position).perform(click());
                            } else if (line.contains("withXPath")) {
                                String xPath = line.substring(line.indexOf("withXPath(\"") + 11, line.indexOf("\")", line.indexOf("withXPath")));
                                onData(anything()).inAdapterView(withXPath(xPath)).atPosition(position).perform(click());
                            }
                        } else if (line.contains("onView")) {
                            int position = -1;
                            if (line.contains("actionOnItemAtPosition")) {
                                position = Integer.parseInt(line.substring(line.indexOf("actionOnItemAtPosition(") + 23, line.indexOf(")", line.indexOf("actionOnItemAtPosition("))));
                            }
                            if (line.contains("withId") && line.contains("withText")) {
                                String id = line.substring(line.indexOf("withId(") + 7, line.indexOf(")", line.indexOf("withId")));
                                int resource_id = InstrumentationRegistry.getTargetContext().getResources().getIdentifier(id.replace("R.id.", ""), "id", InstrumentationRegistry.getTargetContext().getPackageName());
                                String text = line.substring(line.indexOf("withText(\"") + 10, line.indexOf("\")", line.indexOf("withText")));
                                if (line.contains("actionOnItemAtPosition") && line.contains("click")) {
                                    onView(allOf(withId(resource_id), withText(text))).perform(actionOnItemAtPosition(position, click()));
                                } else if (line.contains("longClick")) {
                                    onView(allOf(withId(resource_id), withText(text))).perform(longClick());
                                } else if (line.contains("click")) {
                                    onView(allOf(withId(resource_id), withText(text))).perform(click());
                                } else if (line.contains("replaceText")) {
                                    String replacement_text = line.substring(line.indexOf("replaceText(\"") + 13, line.indexOf("\")", line.indexOf("replaceText")));
                                    onView(allOf(withId(resource_id), withText(text))).perform(replaceText(replacement_text), closeSoftKeyboard());
                                }
                            } else if (line.contains("withId")) {
                                String id = line.substring(line.indexOf("withId(") + 7, line.indexOf(")", line.indexOf("withId")));
                                int resource_id = InstrumentationRegistry.getTargetContext().getResources().getIdentifier(id.replace("R.id.", ""), "id", InstrumentationRegistry.getTargetContext().getPackageName());
                                if (line.contains("actionOnItemAtPosition") && line.contains("click")) {
                                    onView(withId(resource_id)).perform(actionOnItemAtPosition(position, click()));
                                } else if (line.contains("longClick")) {
                                    onView(withId(resource_id)).perform(longClick());
                                } else if (line.contains("click")) {
                                    onView(withId(resource_id)).perform(click());
                                } else if (line.contains("replaceText")) {
                                    String replacement_text = line.substring(line.indexOf("replaceText(\"") + 13, line.indexOf("\")", line.indexOf("replaceText")));
                                    onView(withId(resource_id)).perform(replaceText(replacement_text), closeSoftKeyboard());
                                }
                            } else if (line.contains("withText")) {
                                String text = line.substring(line.indexOf("withText(\"") + 10, line.indexOf("\")", line.indexOf("withText")));
                                if (line.contains("actionOnItemAtPosition") && line.contains("click")) {
                                    onView(withText(text)).perform(actionOnItemAtPosition(position, click()));
                                } else if (line.contains("longClick")) {
                                    onView(withText(text)).perform(longClick());
                                } else if (line.contains("click")) {
                                    onView(withText(text)).perform(click());
                                }
                            } else if (line.contains("withXPath")) {
                                String xPath = line.substring(line.indexOf("withXPath(\"") + 11, line.indexOf("\")", line.indexOf("withXPath")));
                                if (line.contains("actionOnItemAtPosition") && line.contains("click")) {
                                    onView(withXPath(xPath)).perform(actionOnItemAtPosition(position, click()));
                                } else if (line.contains("longClick")) {
                                    onView(withXPath(xPath)).perform(longClick());
                                } else if (line.contains("click")) {
                                    onView(withXPath(xPath)).perform(click());
                                } else if (line.contains("replaceText")) {
                                    String replacement_text = line.substring(line.indexOf("replaceText(\"") + 13, line.indexOf("\")", line.indexOf("replaceText")));
                                    onView(withXPath(xPath)).perform(replaceText(replacement_text), closeSoftKeyboard());
                                }
                            } else if (line.contains("withContentDescription")) {
                                String contentDescription = line.substring(line.indexOf("withContentDescription(\"") + 24, line.indexOf("\")", line.indexOf("withContentDescription")));
                                onView(withContentDescription(contentDescription)).perform(click());
                            }
                        } else if (line.contains("openActionBarOverflowOrOptionsMenu")) {
                            openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getTargetContext());
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addStateAndTransitionToFSM(FiniteStateMachine fsm, State currState, State prevState, Triple<Event, Event, Integer> action) {
        if (prevState != null) {
            if(fsm.getState(prevState) == null) fsm.addState(prevState);
            if (fsm.getState(currState) == null) fsm.addState(currState);
            fsm.addTransition(prevState, currState, new Triple<Event, Boolean, Double>(action.first, true, 0.0));
        } else {
            fsm.addState(currState);
        }
    }

    private void performReplaceText(State currState, FiniteStateMachine fsm, UiNode root) {
        List<Integer> alreadyVisitedStateNodeTargetElements = new ArrayList<Integer>();

        for (int index = 0; index < currState.getActionables().size(); index++) {
            Pair<Event, List<Double>> stateNode = currState.getActionables().get(index);

            UiNode stateNodeTargetElement = stateNode.first.getTargetElement();

            if (alreadyVisitedStateNodeTargetElements.contains(index)) continue;

            String stateNodeClass = stateNodeTargetElement.getAttribute("class");
            if (stateNodeClass.contains("EditText")) {
                String resource_id = stateNodeTargetElement.getAttribute("resource-id");
                if (resource_id != null && resource_id.contains("/"))   resource_id = resource_id.split("/")[1];

                String replacement_text_before = stateNodeTargetElement.getAttribute("replacementtext");

                try {
                    pickBestPossibleInput(root, stateNode.first, stateNodeTargetElement, id_inputType.get(resource_id));
                } catch (IOException e) {
                    e.printStackTrace();
                }

                String replacement_text_after = stateNodeTargetElement.getAttribute("replacementtext");
                if(replacement_text_after != null && replacement_text_after.equals(replacement_text_before))    continue;

                Boolean text_isDigit = false;
                if (stateNodeTargetElement.getAttribute("text") != null)
                    text_isDigit = stateNodeTargetElement.getAttribute("text").matches("-?\\d+(\\.\\d+)?");

                Boolean add = true;
                if (text_isDigit && replacement_text_after != null && !replacement_text_after.matches("-?\\d+(\\.\\d+)?")) {
                    add = false;
                }

                if (replacement_text_after != null && !replacement_text_after.equals(stateNodeTargetElement.getAttribute("text")) && !replacement_text_after.equals("") && add) {
                    StringBuilder xpath = new StringBuilder();
                    findXPath(xpath, stateNodeTargetElement);

                    String actionCode = "";
                    int id = 0;
                    if (resource_id != null)    id = InstrumentationRegistry.getTargetContext().getResources().getIdentifier(resource_id, "id", InstrumentationRegistry.getTargetContext().getPackageName());

                    String text = null;
                    if (!stateNodeTargetElement.getAttribute("class").equals("android.widget.ImageButton") &&
                            stateNodeTargetElement.getAttribute("text") != null && !stateNodeTargetElement.getAttribute("text").equals("")) {
                        text = stateNodeTargetElement.getAttribute("text");
                    }

                    if (id != 0) {
                        try {
                            onView(withId(id)).perform(replaceText(replacement_text_after), closeSoftKeyboard());
                            actionCode = "\nonView(withId(R.id." + resource_id + ")).perform(replaceText(\"" + replacement_text_after + "\"), closeSoftKeyboard());";
                        } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e) {
                            try {
                                onView(withXPath(xpath.toString())).perform(replaceText(replacement_text_after), closeSoftKeyboard());
                                actionCode = "\nonView(withXPath(\"" + xpath.toString() + "\")).perform(replaceText(\"" + replacement_text_after + "\"), closeSoftKeyboard());";
                            } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e1) {
                                if (text != null) {
                                    onView(allOf(withId(id), withText(text))).perform(replaceText(replacement_text_after), closeSoftKeyboard());
                                    actionCode = "\nonView(allOf(withId(R.id." + resource_id + "), withText(\"" + text + "\")" + ")).perform(replaceText(\"" + replacement_text_after + "\"), closeSoftKeyboard());";
                                }
                            }
                        }
                    } else {
                        try {
                            onView(withXPath(xpath.toString())).perform(replaceText(replacement_text_after), closeSoftKeyboard());
                            actionCode = "\nonView(withXPath(\"" + xpath.toString() + "\")).perform(replaceText(\"" + replacement_text_after + "\"), closeSoftKeyboard());";
                        } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e) {
                            if (text != null) {
                                onView(withText(text)).perform(click());
                                actionCode = "\nonView(withText(\"" + text + "\")).perform(click());";
                            }
                        }
                    }

                    if (!actionCode.equals("")) {
                        lines.add(new Quintuple<>(actionCode + "RANDOM", getCurrentState(fsm), new Event(), -1, getActivityInstance().getLocalClassName()));
                        alreadyVisitedStateNodeTargetElements.add(index);
                    }
                }
            }
        }
    }

    private UiNode getRoot() {
        String dumpWindowHierarchy = null;
        try {
            dumpWindowHierarchy = AccessibilityNodeInfoDumper.dumpWindowHierarchy(mDevice);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Unable to dump window hierarchy", e.getMessage());
        }

        if (id_image.size() > 0) {
            boolean needsImageTranslation = false;
            for (String key : id_image.keySet()) {
                if (dumpWindowHierarchy != null && dumpWindowHierarchy.contains(key)) {
                    needsImageTranslation = true;
                    break;
                }
            }

            if (needsImageTranslation) {
                ImageTranslator imageTranslator = new ImageTranslator(id_image);
                String updatedDumpWindowHierarchy = imageTranslator.updateHierarchyAttributes(dumpWindowHierarchy, System.getProperty("user.dir") + "/" + module + "/main/res");
                if (updatedDumpWindowHierarchy != null) {
                    dumpWindowHierarchy = updatedDumpWindowHierarchy;
                }
            }
        }

        UiHierarchyXmlLoader xmlLoader = new UiHierarchyXmlLoader();
        return (UiNode) xmlLoader.parseXml(dumpWindowHierarchy);
    }

    private Triple<Event, Event, Integer> pickRandomAction(State currState, Triple<Event, Event, Integer> nextAction, List<Pair<Event, List<Double>>> state_clickables, Triple<Event, Event, Integer> prevAction) {
        if (nextAction.first.getTargetElement() == null && prevAction != null) {
            int max_longestCommonSubsequence = 0;

            for (Pair<Event, List<Double>> cl : state_clickables) {
                if (cl.first.getTargetElement().getAttribute("class").contains("EditText")) continue;

                List<Double> indexes = cl.second;

                if (indexes.size() == 0 /*&& !cl.first.getTargetElement().getAttribute("content-desc").equals("Navigate up")|| reachableNodesNum > 0*/) {
                    StringBuilder cl_xpath = new StringBuilder();
                    findXPath(cl_xpath, cl.first.getTargetElement());

                    StringBuilder prevAction_xpath = new StringBuilder();
                    findXPath(prevAction_xpath, prevAction.first.getTargetElement());

                    int longestCommonSubsequence = getLongestCommonSubsequence(cl_xpath.toString(), prevAction_xpath.toString());

                    if (longestCommonSubsequence > max_longestCommonSubsequence) {
                        nextAction = new Triple<>(cl.first, null, -1);

                        max_longestCommonSubsequence = longestCommonSubsequence;
                    }
                }
            }

            if (nextAction.first.getTargetElement() != null && ((!nextAction.first.getTargetElement().getAttribute("content-desc").equals("More options")) ||
                    (nextAction.first.getTargetElement().getAttribute("content-desc").equals("More options") &&
                            prevAction.first.getTargetElement().getAttribute("content-desc").equals("More options"))) &&
                    ((!nextAction.first.getTargetElement().getAttribute("content-desc").equals("Open navigation drawer")) ||
                            (nextAction.first.getTargetElement().getAttribute("content-desc").equals("Open navigation drawer") &&
                                    prevAction.first.getTargetElement().getAttribute("content-desc").equals("Open navigation drawer")))) {
                currState.setClickableVisited(nextAction.first, 0);
            }
        }

        if (nextAction.first.getTargetElement() == null) {
            for (Pair<Event, List<Double>> cl : state_clickables) {
                if (cl.first.getTargetElement().getAttribute("class").contains("EditText")) continue;

                List<Double> indexes = cl.second;
                if (indexes.size() == 0) {
                    nextAction = new Triple<>(cl.first, null, -1);
                    if (nextAction.first.getTargetElement() != null && ((!nextAction.first.getTargetElement().getAttribute("content-desc").equals("More options")) ||
                            (nextAction.first.getTargetElement().getAttribute("content-desc").equals("More options") && prevAction != null && prevAction.first.getTargetElement() != null &&
                                    prevAction.first.getTargetElement().getAttribute("content-desc").equals("More options"))) &&
                            ((!nextAction.first.getTargetElement().getAttribute("content-desc").equals("Open navigation drawer")) ||
                                    (nextAction.first.getTargetElement().getAttribute("content-desc").equals("Open navigation drawer") && prevAction != null && prevAction.first.getTargetElement() != null &&
                                            prevAction.first.getTargetElement().getAttribute("content-desc").equals("Open navigation drawer")))) {
                        currState.setClickableVisited(nextAction.first, 0);
                    }

                    break;
                }
            }
        }
        return nextAction;
    }

    private void pickBestPossibleInput(UiNode root, Event stateNode, UiNode stateNodeTargetElement, String inputType) throws IOException {
        double max_Score = 0;
        String stateNodeLabel = "", stateNodeText = "";

        StringBuilder xpath = new StringBuilder();
        findXPath(xpath, stateNodeTargetElement);

        if (stateNodeTargetElement.getAttribute("src") == null || stateNodeTargetElement.getAttribute("src").equals("")) {
            stateNodeText = stateNodeTargetElement.getAttribute("text");
        }

        if (stateNodeText.equals(""))   stateNodeLabel = getEditTextLabel(root, xpath.toString(), stateNodeTargetElement, false);
        else    stateNodeLabel = getEditTextLabel(root, xpath.toString(), stateNodeTargetElement, true);

        if (stateNodeLabel.equals("") && stateNodeTargetElement.getAttribute("resource-id") != null && stateNodeTargetElement.getAttribute("resource-id").contains("/"))
            stateNodeLabel = stateNodeTargetElement.getAttribute("resource-id").split("/")[1];

        String best_replacement_text = null;
        int matchedTransitionIndex = -1;
        boolean found_potential_match = false;
        for (int index = 0; index < scenario.getTransitions().size(); index++) {
            Transition tr = scenario.getTransitions().get(index);
            Event event = tr.getLabel().first;
            if (event != null && event.getTargetElement() != null && event.getTargetElement().getAttribute("class") != null && event.getTargetElement().getAttribute("class").contains("EditText")) {
                double SIMILARITY_THRESHOLD = 0.4;

                Event ev = tr.getLabel().first;
                String ac = ev.getType();

                if (ac.equals("VIEW_TEXT_CHANGED")) {
                    String similarStateNodeLabel = "", similarStateNodeText = "";
                    similarStateNodeLabel = ev.getTargetElement().getAttribute("label");

                    if (ev.getTargetElement().getAttribute("src") == null || ev.getTargetElement().getAttribute("src").equals("")) {
                        similarStateNodeText = ev.getTargetElement().getAttribute("text");
                        //similarStateNodeText = EliminateVerbs(lemmatizationAndPOSTagger, similarStateNodeText);
                    }

                    if ((similarStateNodeLabel == null || similarStateNodeLabel.equals("")) /*&& (similarStateNodeText == null || similarStateNodeText.equals(""))*/ && ev.getTargetElement().getAttribute("resource-id") != null && ev.getTargetElement().getAttribute("resource-id").contains("/")) {
                        similarStateNodeLabel = ev.getTargetElement().getAttribute("resource-id").split("/")[1];
                    }

                    if (similarStateNodeLabel != null && best_replacement_text != null && similarStateNodeLabel.equals(best_replacement_text)) continue;

                    double score1 = 0.0;
                    if (!stateNodeLabel.equals("") && !similarStateNodeLabel.equals(""))
                        score1 = compareTextWithLemmatization(stateNodeLabel, similarStateNodeLabel);

                    double score2 = 0.0;
                    if (!stateNodeText.equals("") && !similarStateNodeText.equals(""))
                        score2 = compareTextWithLemmatization(stateNodeText, similarStateNodeText);

                    double score3 = 0.0;
                    if (!stateNodeLabel.equals("") && !similarStateNodeText.equals(""))
                        score3 = compareTextWithLemmatization(stateNodeLabel, similarStateNodeText);

                    double score4 = 0.0;
                    if (!stateNodeText.equals("") && !similarStateNodeLabel.equals(""))
                        score4 = compareTextWithLemmatization(stateNodeText, similarStateNodeLabel);

                    double score = Math.max(score1, Math.max(score2, Math.max(score3, score4)));

                    if (score <= 0.2) {
                        double delta = stringDiff(stateNodeLabel, similarStateNodeLabel);
                        double sim = Math.max(stateNodeLabel.length(), similarStateNodeLabel.length()) - delta;
                        score1 = sim / (sim + delta);

                        delta = stringDiff(stateNodeText, similarStateNodeText);
                        sim = Math.max(stateNodeText.length(), similarStateNodeText.length()) - delta;
                        score2 = sim / (sim + delta);

                        delta = stringDiff(stateNodeLabel, similarStateNodeText);
                        sim = Math.max(stateNodeLabel.length(), similarStateNodeText.length()) - delta;
                        score3 = sim / (sim + delta);

                        delta = stringDiff(stateNodeText, similarStateNodeLabel);
                        sim = Math.max(stateNodeText.length(), similarStateNodeLabel.length()) - delta;
                        score4 = sim / (sim + delta);

                        score = Math.max(score1, Math.max(score2, Math.max(score3, score4)));
                    }

                    if ((score > max_Score || (score == max_Score && !tr.getLabel().second && !found_potential_match)) && score >= SIMILARITY_THRESHOLD /*&& (!stateNodeTargetElement.getAttribute("text").equals(ev.getReplacementText()) || stateNodeTargetElement.getAttribute("text").equals(""))*/) {
                        if (score > max_Score && !tr.getLabel().second)   found_potential_match = true;
                        boolean add = true;
                        if(inputType != null && ((inputType.contains("number") && !ev.getReplacementText().trim().matches("\\d+(?:\\.\\d+)?")) ||
                                (!inputType.contains("number") && ev.getReplacementText().trim().matches("\\d+(?:\\.\\d+)?"))))    add = false;

                        if (add) {
                            best_replacement_text = ev.getReplacementText();

                            max_Score = score;
                            matchedTransitionIndex = index;

                            if (index > lastVisitedTransitionIndex) lastVisitedTransitionIndex = index;
                        }
                    }
                }
            }
        }

        List<Transition> new_transitions = new ArrayList<>();
        for (int index = 0; index < scenario.getTransitions().size(); index++) {
            if (index == matchedTransitionIndex) {
                Transition matchedTransition = scenario.getTransitions().get(index);
                Transition newTr = new Transition(matchedTransition.getFrom(), matchedTransition.getTo(), new Triple<>(matchedTransition.getLabel().first, true, 0.0));
                new_transitions.add(newTr);
            } else {
                new_transitions.add(scenario.getTransitions().get(index));
            }
        }
        scenario.setTransitions(new_transitions);

        if (best_replacement_text != null) {
            stateNodeTargetElement.addAtrribute("replacementtext", best_replacement_text);
            stateNode.setReplacementText(best_replacement_text);
        }
    }

    private State getCurrentState(FiniteStateMachine fsm) {
        State currState;

        String dumpWindowHierarchy = null;
        try {
            dumpWindowHierarchy = AccessibilityNodeInfoDumper.dumpWindowHierarchy(mDevice);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Unable to dump window hierarchy", e.getMessage());
        }

        UiNode root = getRoot();
        updateCheckBoxText(root);

        StateAbstraction abs = new StateAbstraction();
        currState = new State(dumpWindowHierarchy, abs.computeFeatureVector(root), findInteractables(root, new ArrayList<Pair<Event, List<Double>>>(), false)/*getActionables(root)*/, getCurrentActivityWithPackage());

        if (fsm.getState(currState) != null) {
            currState = fsm.getState(currState);
        }

        return currState;
    }

    public static int getLongestCommonSubsequence(String a, String b) {
        int m = a.length();
        int n = b.length();
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 0; i <= m; i++) {
            for (int j = 0; j <= n; j++) {
                if (i == 0 || j == 0) {
                    dp[i][j] = 0;
                } else if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = 1 + dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        return dp[m][n];
    }

    private static Activity getActivityInstance() {
        final Activity[] currentActivity = new Activity[1];
        getInstrumentation().runOnMainSync(new Runnable() {
            public void run() {
                Collection resumedActivities = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(RESUMED);
                if (resumedActivities.iterator().hasNext()) {
                    currentActivity[0] = (Activity) resumedActivities.iterator().next();
                }
            }
        });

        return currentActivity[0];
    }

    private String getCurrentActivityWithPackage() {
        Activity activityInstance = getActivityInstance();
        if (activityInstance != null) {
            String localClassName = activityInstance.getLocalClassName();
            if (localClassName.contains("."))   return localClassName;
            else    return activityInstance.getPackageName() + "." + localClassName;
        }
        else return "";
    }

    private String selectAssertionTargetElement(UiNode targetElement) {
        String actionCode = "";
        int id = 0;
        String resource_id = "";
        if (targetElement.getAttribute("resource-id") != null && targetElement.getAttribute("resource-id").contains("/")) {
            resource_id = targetElement.getAttribute("resource-id").split("/")[1];
            id = InstrumentationRegistry.getTargetContext().getResources().getIdentifier(resource_id, "id", InstrumentationRegistry.getTargetContext().getPackageName());
        }

        String text = null;
        if (!targetElement.getAttribute("class").equals("android.widget.ImageButton") && targetElement.getAttribute("text") != null &&
                !targetElement.getAttribute("text").equals("")) {
            text = targetElement.getAttribute("text");
        }

        if (id != 0) {
            try {
                onView(withId(id)).check(matches(isDisplayed()));
                actionCode = "\nonView(withId(R.id." + resource_id + "))";
            } catch (RuntimeException e) {
                if (text != null) {
                    try {
                        onView(allOf(withId(id), withText(text))).check(matches(isDisplayed()));
                        actionCode = "\nonView(allOf(withId(R.id." + resource_id + "), withText(\"" + text + "\")" + "))";
                    } catch (RuntimeException e1) {
                        try {
                            StringBuilder xpath = new StringBuilder();
                            findXPath(xpath, targetElement);
                            onView(withXPath(xpath.toString())).check(matches(isDisplayed()));
                            actionCode = "\nonView(withXPath(\"" + xpath.toString() + "\"))";
                        } catch (RuntimeException e2) {
                            return "";
                        }
                    }
                } else {
                    try {
                        StringBuilder xpath = new StringBuilder();
                        findXPath(xpath, targetElement);
                        onView(withXPath(xpath.toString())).check(matches(isDisplayed()));
                        actionCode = "\nonView(withXPath(\"" + xpath.toString() + "\"))";
                    } catch (RuntimeException e1) {
                        return "";
                    }
                }
            }
        } else {
            if (text != null) {
                try {
                    onView(withText(text)).check(matches(isDisplayed()));
                    actionCode = "\nonView(withText(\"" + text + "\"))";
                } catch (RuntimeException e) {
                    try {
                        StringBuilder xpath = new StringBuilder();
                        findXPath(xpath, targetElement);
                        onView(withXPath(xpath.toString())).check(matches(isDisplayed()));
                        actionCode = "\nonView(withXPath(\"" + xpath.toString() + "\"))";
                    } catch (RuntimeException e1) {
                        return "";
                    }
                }
            } else {
                try {
                    StringBuilder xpath = new StringBuilder();
                    findXPath(xpath, targetElement);
                    onView(withXPath(xpath.toString())).check(matches(isDisplayed()));
                    actionCode = "\nonView(withXPath(\"" + xpath.toString() + "\"))";
                } catch (RuntimeException e1) {
                    return "";
                }
            }
        }

        return actionCode;
    }

    private Boolean checkIfNodeIsListViewOrRecyclerView (UiNode node) {
        return node.getAttribute("class").equals("android.widget.ListView") || node.getAttribute("class").equals("android.support.v7.widget.RecyclerView");
    }

    private void performAction(Triple<Event, Event, Integer> nextActionTriple, FiniteStateMachine fsm) throws IOException {
        String random = "~RANDOM";
        Event nextAction = nextActionTriple.first;
        Event correspondingAction = nextActionTriple.second;

        String actionCode = null;

        if (nextAction == null) {
            if (pressBack) {
                try {
                    Espresso.pressBack();
                    actionCode = "\npressBack();RANDOM";
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    Log.e("RuntimeException", e.getMessage());
                }
                pressBack = false;
            }
        } else {
            String type = nextAction.getType();
            UiNode nextActionTargetElement = nextAction.getTargetElement();
            String replacement_text = nextActionTargetElement.getAttribute("replacementtext");

            StringBuilder xpath = new StringBuilder();
            findXPath(xpath, nextAction.getTargetElement());

            int id = 0;
            String resource_id = "";
            if (nextActionTargetElement.getAttribute("resource-id") != null && nextActionTargetElement.getAttribute("resource-id").contains("/")) {
                resource_id = nextActionTargetElement.getAttribute("resource-id").split("/")[1];
                id = InstrumentationRegistry.getTargetContext().getResources().getIdentifier(resource_id, "id", InstrumentationRegistry.getTargetContext().getPackageName());
            }

            String text = null;
            if (!nextActionTargetElement.getAttribute("class").equals("android.widget.ImageButton") && nextActionTargetElement.getAttribute("text") != null && !nextActionTargetElement.getAttribute("text").equals("")) {
                text = nextActionTargetElement.getAttribute("text");
            }

            if (correspondingAction != null && correspondingAction.getType() != null) {
                type = correspondingAction.getType();
            }

            UiNode listViewChildNode = nextActionTargetElement;
            UiNode listViewNode = (UiNode) nextActionTargetElement.getParent();
            while (listViewNode != null && !checkIfNodeIsListViewOrRecyclerView(listViewNode)) {
                listViewChildNode = listViewNode;
                listViewNode = (UiNode) listViewNode.getParent();
            }

            String listView_resource_id;
            if (listViewNode != null && (listViewNode.getAttribute("class").equals("android.widget.ListView") ||
                    listViewNode.getAttribute("class").equals("android.support.v7.widget.RecyclerView")) &&
                    listViewNode.getAttribute("resource-id") != null && listViewNode.getAttribute("resource-id").contains("/")) {
                listView_resource_id = listViewNode.getAttribute("resource-id").split("/")[1];
                text = "";
            } else listView_resource_id = resource_id;

            if (navigationDrawerIsOpen && !resource_id.equals("") && listView_resource_id.equals(resource_id)) {
                try {
                    onView(withContentDescription("Close navigation drawer")).perform(click());
                    actionCode = "\nonView(withContentDescription(\"Close navigation drawer\")).perform(click());";

                    recordEvent(nextActionTriple, random, fsm, null, actionCode);
                    actionCode = null;
                    navigationDrawerIsOpen = false;
                } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e) {
                    // do nothing
                }
            }

            try {
                if (type.contains("VIEW_CLICKED")) {
                    String contentDesc = nextActionTargetElement.getAttribute("content-desc");
                    if (contentDesc.equals("More options")) {
                        openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getTargetContext());
                        actionCode = "\nopenActionBarOverflowOrOptionsMenu(getInstrumentation().getTargetContext());";
                    } else {
                        String clazz = nextActionTargetElement.getAttribute("class");
                        if (clazz.contains("Image") || (text != null && text.contains("\n")) || nextActionTargetElement.getAttribute("src") != null) text = null;

                        if (id != 0) {
                            try {
                                onView(withId(id)).perform(click());
                                actionCode = "\nonView(withId(R.id." + resource_id + ")).perform(click());";
                            } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e) {
                                try {
                                    onView(withXPath(xpath.toString())).perform(click());
                                    actionCode = "\nonView(withXPath(\"" + xpath.toString() + "\")).perform(click());";
                                } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e1) {
                                    if (text != null) {
                                        try {
                                            onView(allOf(withId(id), withText(text))).perform(click());
                                            actionCode = "\nonView(allOf(withId(R.id." + resource_id + "), withText(\"" + text + "\")" + ")).perform(click());";
                                        } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e2) {
                                            try {
                                                onView(withXPath(xpath.toString())).perform(click());
                                                actionCode = "\nonView(withXPath(\"" + xpath.toString() + "\")).perform(click());";
                                            } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e3) {
                                                if (listViewNode != null) {
                                                    int positionIndex = -1;
                                                    for (int i = 0; i < listViewNode.getChildren().length; i++) {
                                                        if (listViewNode.getChildren()[i].toString().equals(listViewChildNode.toString())) {
                                                            positionIndex = i;
                                                            break;
                                                        }
                                                    }

                                                    int parentNode_id = 0;
                                                    String parentNode_resource_id = "";
                                                    if (listViewNode.getAttribute("resource-id") != null && listViewNode.getAttribute("resource-id").contains("/")) {
                                                        parentNode_resource_id = listViewNode.getAttribute("resource-id").split("/")[1];
                                                        parentNode_id = InstrumentationRegistry.getTargetContext().getResources().getIdentifier(parentNode_resource_id, "id", InstrumentationRegistry.getTargetContext().getPackageName());
                                                    }

                                                    try {
                                                        if (parentNode_id == 0) {
                                                            StringBuilder listView_xpath = new StringBuilder();
                                                            findXPath(listView_xpath, listViewNode);
                                                            onData(anything()).inAdapterView(withXPath(listView_xpath.toString())).atPosition(positionIndex).perform(click());
                                                            actionCode = "\nonData(anything()).inAdapterView(withXPath(\"" + listView_xpath.toString() + "\")).atPosition(" + positionIndex + ").perform(click());";
                                                        } else {
                                                            onData(anything()).inAdapterView(withId(parentNode_id)).atPosition(positionIndex).perform(click());
                                                            actionCode = "\nonData(anything()).inAdapterView(withId(R.id." + parentNode_resource_id + ")).atPosition(" + positionIndex + ").perform(click());";
                                                        }
                                                    } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e4) {
                                                        try {
                                                            if (parentNode_id == 0) {
                                                                StringBuilder listView_xpath = new StringBuilder();
                                                                findXPath(listView_xpath, listViewNode);
                                                                onView(withXPath(listView_xpath.toString())).perform(actionOnItemAtPosition(positionIndex, click()));
                                                                actionCode = "\nonView(withXPath(\"" + listView_xpath.toString() + "\")).perform(actionOnItemAtPosition(" + positionIndex + ", click()));";
                                                            } else {
                                                                onView(withId(parentNode_id)).perform(actionOnItemAtPosition(positionIndex, click()));
                                                                actionCode = "\nonView(withId(R.id." + parentNode_resource_id + ")).perform(actionOnItemAtPosition(" + positionIndex + ", click()));";
                                                            }
                                                        } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e5) {
                                                            e5.printStackTrace();
                                                            Log.e("RuntimeException", e5.getMessage());
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        try {
                                            onView(withXPath(xpath.toString())).perform(click());
                                            actionCode = "\nonView(withXPath(\"" + xpath.toString() + "\")).perform(click());";
                                        } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e2) {
                                            e2.printStackTrace();
                                            Log.e("RuntimeException", e2.getMessage());
                                        }
                                    }
                                }
                            }
                            navigationDrawerIsOpen = false;
                        } else {
                            try {
                                if (contentDesc != null && !contentDesc.equals("")) {
                                    onView(withContentDescription(contentDesc)).perform(click());
                                    actionCode = "\nonView(withContentDescription(\"" + contentDesc + "\")).perform(click());";
                                    if (contentDesc.equals("Open navigation drawer"))   navigationDrawerIsOpen = true;
                                    if (contentDesc.equals("Close navigation drawer"))   navigationDrawerIsOpen = false;
                                } else {
                                    onView(withXPath(xpath.toString())).perform(click());
                                    actionCode = "\nonView(withXPath(\"" + xpath.toString() + "\")).perform(click());";
                                    navigationDrawerIsOpen = false;
                                }
                            } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e) {
                                try {
                                    onView(withXPath(xpath.toString())).perform(click());
                                    actionCode = "\nonView(withXPath(\"" + xpath.toString() + "\")).perform(click());";
                                    navigationDrawerIsOpen = false;
                                } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e1) {
                                    if (text != null) {
                                        onView(withText(text)).perform(click());
                                        actionCode = "\nonView(withText(\"" + text + "\")).perform(click());";
                                        navigationDrawerIsOpen = false;
                                    }
                                }
                            }
                        }
                    }
                } else if (type.equals("VIEW_LONG_CLICKED")) {
                    if (id != 0) {
                        try {
                            onView(withId(id)).perform(longClick());
                            actionCode = "\nonView(withId(R.id." + resource_id + ")).perform(longClick());";
                        } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e) {
                            try {
                                onView(withXPath(xpath.toString())).perform(longClick());
                                actionCode = "\nonView(withXPath(\"" + xpath.toString() + "\")).perform(longClick());";
                            } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e1) {
                                if (text != null) {
                                    onView(allOf(withId(id), withText(text))).perform(longClick());
                                    actionCode = "\nonView(allOf(withId(R.id." + resource_id + "), withText(\"" + text + "\")" + ")).perform(longClick());";
                                }
                            }
                        }
                    } else {
                        try {
                            onView(withXPath(xpath.toString())).perform(longClick());
                            actionCode = "\nonView(withXPath(\"" + xpath.toString() + "\")).perform(longClick());";
                        } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e) {
                            if (text != null) {
                                onView(withText(text)).perform(longClick());
                                actionCode = "\nonView(withText(\"" + text + "\")).perform(longClick());";
                            }
                        }
                    }
                    navigationDrawerIsOpen = false;
                } else if (type.equals("LIST_ITEM_CLICKED")) {
                    UiNode parentNode = (UiNode) nextActionTargetElement.getParent();
                    int positionIndex = -1;
                    for (int i = 0; i < parentNode.getChildren().length; i++) {
                        if (parentNode.getChildren()[i].toString().equals(nextActionTargetElement.toString())) {
                            positionIndex = i;
                        }
                    }

                    while (parentNode != null && !parentNode.getAttribute("class").equals("android.support.v7.widget.RecyclerView")) {
                        parentNode = (UiNode) parentNode.getParent();
                    }

                    xpath = new StringBuilder();
                    if (parentNode != null) findXPath(xpath, parentNode);

                    if (positionIndex != -1) {
                        if (id != 0) {
                            try {
                                onView(withId(id)).perform(actionOnItemAtPosition(positionIndex, click()));
                                actionCode = "\nonView(withId(R.id." + resource_id + ")).perform(actionOnItemAtPosition(" + positionIndex + ", click()));";
                            } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e) {
                                try {
                                    onView(withXPath(xpath.toString())).perform(actionOnItemAtPosition(positionIndex, click()));
                                    actionCode = "\nonView(withXPath(\"" + xpath.toString() + "\")).perform(actionOnItemAtPosition(" + positionIndex + ", click()));";
                                } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e1) {
                                    if (text != null) {
                                        onView(allOf(withId(id), withText(text))).perform(actionOnItemAtPosition(positionIndex, click()));
                                        actionCode = "\nonView(allOf(withId(R.id." + resource_id + "), withText(\"" + text + "\")" + ")).perform(actionOnItemAtPosition(" + positionIndex + ", click()));";
                                    }
                                }
                            }
                        } else {
                            try {
                                onView(withXPath(xpath.toString())).perform(actionOnItemAtPosition(positionIndex, click()));
                                actionCode = "\nonView(withXPath(\"" + xpath.toString() + "\")).perform(actionOnItemAtPosition(" + positionIndex + ", click()));";
                            } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e) {
                                if (text != null) {
                                    onView(withText(text)).perform(actionOnItemAtPosition(positionIndex, click()));
                                    actionCode = "\nonView(withText(\"" + text + "\")).perform(actionOnItemAtPosition(" + positionIndex + ", click()));";
                                }
                            }
                        }
                    }
                    navigationDrawerIsOpen = false;
                } else if (type.equals("VIEW_TEXT_CHANGED")) {
                    if (replacement_text == null)
                        replacement_text = nextActionTargetElement.getAttribute("text");
                    if (replacement_text != null) {
                        try {
                            onView(withXPath(xpath.toString())).perform(replaceText(replacement_text), closeSoftKeyboard());
                            actionCode = "\nonView(withXPath(\"" + xpath.toString() + "\")).perform(replaceText(\"" + replacement_text + "\"), closeSoftKeyboard());";
                        } catch (AmbiguousViewMatcherException | NoMatchingViewException e) {
                            if (id != 0 && text != null) {
                                onView(allOf(withId(id), withText(text))).perform(replaceText(replacement_text), closeSoftKeyboard());
                                actionCode = "\nonView(allOf(withId(" + id + "), withText(\"" + text + "\")" + "))).perform(replaceText(\"" + replacement_text + "\"), closeSoftKeyboard());";
                            } else if (id != 0) {
                                onView(withId(id)).perform(replaceText(replacement_text), closeSoftKeyboard());
                                actionCode = "\nonView(withId(" + id + ")).perform(replaceText(\"" + replacement_text + "\"), closeSoftKeyboard());";
                            }
                        }
                    }

                    navigationDrawerIsOpen = false;
                }
            } catch (NoMatchingViewException e) {
                e.printStackTrace();
                Log.e("NoMatchingViewException", e.getMessage());
            } catch (PerformException e) {
                e.printStackTrace();
                Log.e("PerformException", e.getMessage());
            } catch (RuntimeException e) {
                e.printStackTrace();
                Log.e("RuntimeException", e.getMessage());
            }
        }

        recordEvent(nextActionTriple, random, fsm, nextAction, actionCode);

        if (pressBack) {
            State currState = getCurrentState(fsm);
            performReplaceText(currState, fsm, getRoot());

            try {
                Espresso.pressBack();
                actionCode = "\npressBack();~RANDOM";

                String act = null;
                if (getActivityInstance() != null)
                    act = getActivityInstance().getLocalClassName();
                lines.add(new Quintuple<>(actionCode, getCurrentState(fsm), nextAction, nextActionTriple.third, act));

                pressBack = false;
            } catch(RuntimeException e){
                e.printStackTrace();
                Log.e("RuntimeException", e.getMessage());
            }
        }
    }

    private void recordEvent(Triple<Event, Event, Integer> nextActionTriple, String random, FiniteStateMachine fsm, Event nextAction, String actionCode) {
        String activity = null;
        if (getActivityInstance() != null) activity = getActivityInstance().getLocalClassName();
        if(actionCode != null && !actionCode.equals("")) lines.add(new Quintuple<>(actionCode + random, getCurrentState(fsm), nextAction, nextActionTriple.third, activity));
    }

    private void findXPath(StringBuilder xpath, UiNode node) {
        UiNode parent = (UiNode) node.getParent();
        if (parent == null) {
            xpath.insert(0, "/" + node.getAttribute("class"));
            return;
        }

        int parentChildrenCount = parent.getChildCount();
        if (parentChildrenCount > 1) {
            int index = 1;
            String className = node.getAttribute("class");
            int nodeIndex = parent.getChildrenList().indexOf(node);
            for (int i = 0; i < nodeIndex; i++) {
                if (((UiNode) parent.getChildrenList().get(i)).getAttribute("class").equals(className))
                    index++;
            }
            if (index != 1) xpath.insert(0, "/" + className + "[" + index + "]");
            else xpath.insert(0, "/" + className);
        } else {
            xpath.insert(0, "/" + node.getAttribute("class"));
        }

        findXPath(xpath, parent);
    }

    private List<Pair<Assertion, Event>> findSimilarAssertions(State state) {
        List<Pair<Assertion, Event>> matchedAssertions = new ArrayList<Pair<Assertion, Event>>();
        for (Assertion assertion : assertions) {
            if (assertion.getVisited()) continue;
            UiNode assertionTargetElement = assertion.getTargetElement();
            Event matchedNode = null;
            if (assertionTargetElement != null)   matchedNode = findMatchedActionable(state, assertionTargetElement);
            if (matchedNode != null) {
                matchedAssertions.add(new Pair<Assertion, Event>(assertion, matchedNode));
            }
        }
        return matchedAssertions;
    }

    private Event findMatchedActionable(State state, UiNode assertionTargetElement) {
        double max_score = -1;
        Event matchedNode = null;
        for (Pair<Event, List<Double>> InteractableNode : state.getActionables()) {
            UiNode stateNodeTargetElement = InteractableNode.first.getTargetElement();
            String eventTargetElementText = assertionTargetElement.getAttribute("text");
            String eventTargetElementClass = assertionTargetElement.getAttribute("class");

            if (eventTargetElementText == null || eventTargetElementText.equals(""))
                eventTargetElementText = assertionTargetElement.getAttribute("content-desc");
            if (assertionTargetElement.getAttribute("src") != null && !assertionTargetElement.getAttribute("src").equals("")
                    && assertionTargetElement.getAttribute("resource-id") != null && assertionTargetElement.getAttribute("resource-id").contains("/"))
                eventTargetElementText += " " + assertionTargetElement.getAttribute("resource-id").split("/")[1];

            String stateNodeTargetElementText = stateNodeTargetElement.getAttribute("text");
            String stateNodeTargetElementClass = stateNodeTargetElement.getAttribute("class");

            if ((eventTargetElementClass.contains("EditText") && !stateNodeTargetElementClass.contains("EditText")) ||
                    (!eventTargetElementClass.contains("EditText") && stateNodeTargetElementClass.contains("EditText")) ||
                    (!eventTargetElementClass.contains("CheckBox") && stateNodeTargetElementClass.contains("CheckBox")) ||
                    (eventTargetElementClass.contains("CheckBox") && !stateNodeTargetElementClass.contains("CheckBox")))    continue;

            if (stateNodeTargetElementText == null || stateNodeTargetElementText.equals(""))
                stateNodeTargetElementText = stateNodeTargetElement.getAttribute("content-desc");
            if (stateNodeTargetElement.getAttribute("src") != null && !stateNodeTargetElement.getAttribute("src").equals("")
                    && stateNodeTargetElement.getAttribute("resource-id") != null && stateNodeTargetElement.getAttribute("resource-id").contains("/"))
                stateNodeTargetElementText += " " + stateNodeTargetElement.getAttribute("resource-id").split("/")[1];

            double textMatchingScore;
            if (eventTargetElementText != null && stateNodeTargetElementText != null && !eventTargetElementText.equals("") && !stateNodeTargetElementText.equals("")) {
                String currentStateLeafNodeText = stateNodeTargetElementText.toLowerCase();
                String scenarioStateLeafNodeText = eventTargetElementText.toLowerCase();
                textMatchingScore = compareTextWithLemmatization(currentStateLeafNodeText, scenarioStateLeafNodeText);

                if (textMatchingScore >= 0.6 && textMatchingScore > max_score) {
                    max_score = textMatchingScore;
                    matchedNode = InteractableNode.first;
                }
            }
        }
        return matchedNode;
    }

    private static String filterWord(String str) {
        if (str == null)    return "";

        str = str.toLowerCase();

        List<String> wordsToFilter = new ArrayList<String>() {{
            add("highlight");
            add("action");
            add("input");
            add("content");
            add("white");
            add("black");
            add("autocomplete");
            add("circle");
            add("menu");
            add("-web");
            add("outline");
            add("button");
            add("sign");
            add("red");
            add("default");
            add("mode");
            add("arrow");
            add("editor");
            add("edittext");
            add("fab");
            add("grey");
            add("...");
            add("action");
            add("picker");
            add("text");
            add("$");
            add(":");
        }};

        List<String> additionalWordsToFilter = new ArrayList<String>() {{
            add("ic");
            add("dp");
            add("sp");
        }};
        additionalWordsToFilter.addAll(wordsToFilter);

        for (String wordToFilter : wordsToFilter) {
            if (str.contains(wordToFilter)) {
                int startIndex = str.indexOf(wordToFilter);
                int endIndex = str.length() - 1;
                int splitIndex = str.indexOf(" ", startIndex);
                if (str.indexOf("_", startIndex) > -1 && str.indexOf("_", startIndex) < splitIndex)
                    splitIndex = str.indexOf("_", startIndex);
                if (splitIndex > -1)    endIndex = splitIndex;
                str = str.replace(str.substring(startIndex, endIndex + 1), "");
            }
        }

        StringBuilder strResult = new StringBuilder();
        String[] splittedStr = str.split("[_]");
        if (splittedStr.length > 0) {
            for (int i = 0; i < splittedStr.length; i++) {
                if (additionalWordsToFilter.contains(splittedStr[i])) {
                    if (i != 0 && i != splittedStr.length - 1) strResult.append(" ");
                } else {
                    if (i != splittedStr.length - 1) strResult.append(splittedStr[i]).append(" ");
                    else strResult.append(splittedStr[i]);
                }
            }
        } else if (additionalWordsToFilter.contains(str)) return "";

        String filteredWord = strResult.toString();

        try {
            double strNumber = Double.parseDouble(filteredWord);
            long iPart = (long) strNumber;
            double fPart = strNumber - iPart;
            if (fPart == 0) filteredWord = Long.toString(iPart);
        } catch (NumberFormatException e) {
            // do nothing
        }

        return filteredWord.replace("/", " ").replaceAll("\\s*-\\s*", "").replace("(", " ").replace(")", " ").toLowerCase();
    }

    private double compareTextWithLemmatization(String currentStateLeafNodeText, String scenarioStateLeafNodeText) {
        if (!scenarioStateLeafNodeText.trim().equals("") && !currentStateLeafNodeText.trim().equals("") && scenarioStateLeafNodeText.trim().equals(currentStateLeafNodeText.trim())) {
            return 1 + (currentStateLeafNodeText.length() - currentStateLeafNodeText.replace(" ", "").length());
        }

        Map<Pair<String, String>, Double> scores_map = new HashMap<>();
        List<String> currentStateLeafNodeLemmatizedText = null;
        try {
            currentStateLeafNodeLemmatizedText = lemmatizationAndPOSTagger.getLemmatizedWord(filterWord(currentStateLeafNodeText));
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Unable to get lemmatized word for " + currentStateLeafNodeText, e.getMessage());
        }
        List<String> scenarioStateLeafNodeLemmatizedText = null;
        try {
            scenarioStateLeafNodeLemmatizedText = lemmatizationAndPOSTagger.getLemmatizedWord(filterWord(scenarioStateLeafNodeText));
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Unable to get lemmatized word for " + scenarioStateLeafNodeText, e.getMessage());
        }

        if (currentStateLeafNodeLemmatizedText != null && scenarioStateLeafNodeLemmatizedText != null) {
            int currentStateLeafNodeLemmatizedTextSize = currentStateLeafNodeLemmatizedText.size();

            int currentStateLeafNodeLemmatizedTextCount = 0, scenarioStateLeafNodeLemmatizedTextCount = 0;
            int numberOfSimilarTokens = 0, numberOfMatchedTokens = 0;
            for (String currentStateText : currentStateLeafNodeLemmatizedText) {
                scenarioStateLeafNodeLemmatizedTextCount = 0;
                for (String scenarioStateText : scenarioStateLeafNodeLemmatizedText) {
                    int scenarioStateLeafNodeLemmatizedTextSize = scenarioStateLeafNodeLemmatizedText.size();
                    String key = currentStateText + scenarioStateText;
                    double score = 0;
                    if (currentStateText.equals(scenarioStateText)) {
                        numberOfSimilarTokens++;
                        score = 0.6;
                    } else if (dictionary.containsKey(key)) {
                        score = dictionary.get(key);
                    } else {
                        try {
                            score = computeSimilarityScore(currentStateText, scenarioStateText);
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.e("Unable to compute similarity score " + currentStateLeafNodeText, e.getMessage());
                        }
                        if (score < 0.4) {
                            double delta = stringDiff(filterWord(currentStateText), filterWord(scenarioStateText));
                            double sim = Math.max(filterWord(currentStateText).length(), filterWord(scenarioStateText).length()) - delta;
                            if ((sim / (sim + delta)) >= 0.8)   score = sim / (sim + delta);
                        }
                    }

                    dictionary.put(key, score);
                    if (score >= 0.4) {
                        numberOfMatchedTokens++;
                        scores_map.put(new Pair<>(currentStateText, scenarioStateText), score);
                    }
                }
                currentStateLeafNodeLemmatizedTextCount++;
            }

            double avgScores = 0;
            if (currentStateLeafNodeLemmatizedTextSize > 0 && scores_map.size() > 0)
                avgScores = getAvgScores(currentStateLeafNodeLemmatizedTextSize, scores_map).second;

            if (numberOfSimilarTokens > 2)  avgScores += numberOfSimilarTokens * 0.05;

            return avgScores;
        } else return 0;
    }

    private Double computeSimilarityScore(String vocab1, String vocab2) throws IOException {
        Request request = new Request.Builder()
                .url("http://10.0.3.2:5000/")
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "{\"vocab1\": \"" + vocab1 + "\", \"vocab2\": \"" + vocab2 + "\"}"))
                .build();
        Response response = client.newCall(request).execute();
        String result = "";
        if(response.body() != null) result = response.body().string();
        try {
            return Double.parseDouble(result);
        }
        catch(NumberFormatException e) {
            return 0.0;
        }
    }

    private <T> Pair<Map<Pair<T, T>, Double>, Double> getAvgScores(int size, Map<Pair<T, T>, Double> scores_map) {
        Map<Pair<T, T>, Double> scores_map_sorted = sortScores(scores_map);

        List<T> alreadyPickedScenarioStateTexts = new ArrayList<>();
        List<T> alreadyPickedCurrentStateText = new ArrayList<>();
        Map<Pair<T, T>, Double> scores_map_highest_Score = new LinkedHashMap<>();
        int counter = 0;
        for (Pair<T, T> key : scores_map_sorted.keySet()) {
            T currentStateText = key.first;
            T scenarioStateText = key.second;

            if (!alreadyPickedScenarioStateTexts.contains(scenarioStateText) && !alreadyPickedCurrentStateText.contains(currentStateText) && counter < size) {
                scores_map_highest_Score.put(key, scores_map_sorted.get(key));

                alreadyPickedScenarioStateTexts.add(scenarioStateText);
                alreadyPickedCurrentStateText.add(currentStateText);

                counter++;
            }
        }

        double sumScores = 0;
        counter = 0;
        for (Pair<T, T> key : scores_map_highest_Score.keySet()) {
            if (scores_map_highest_Score.get(key) != 0 && scores_map_highest_Score.get(key) >= 0.4) {
                sumScores += scores_map_highest_Score.get(key);
                counter++;
            }
        }

        return new Pair<>(scores_map_highest_Score, sumScores);
    }

    private static <T> Map<T, Double> sortScores(Map<T, Double> map) {
        List<Entry<T, Double>> list = new LinkedList<>(map.entrySet());

        Collections.sort(list, new Comparator<Entry<T, Double>>() {
            public int compare(Entry<T, Double> o1,
                               Entry<T, Double> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });

        Map<T, Double> map_sorted = new LinkedHashMap<>();
        for (Entry<T, Double> entry : list) {
            map_sorted.put(entry.getKey(), entry.getValue());
        }

        return map_sorted;
    }

    public static int stringDiff(CharSequence s, CharSequence t) {
        int n = s.length();
        int m = t.length();
        if (n == 0) return m;
        if (m == 0) return n;

        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) d[i][0] = i;
        for (int j = 0; j <= m; j++) d[0][j] = j;

        for (int i = 1; i <= n; ++i) {
            char s_i = s.charAt(i - 1);
            for (int j = 1; j <= m; ++j) {
                char t_j = t.charAt(j - 1);
                int cost = (s_i == t_j ? 0 : 1);
                d[i][j] = min3(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost);
            }
        }

        return d[n][m];
    }

    private static int min3(int a, int b, int c) {
        if (b < a) a = b;
        if (c < a) a = c;
        return a;
    }

    private void findLeafNodes(UiNode node, List<Pair<UiNode, Boolean>> leafNodes, Boolean longClickable) {
        if (node.getChildCount() == 0 && !node.getAttribute("class").equals("android.support.v7.widget.RecyclerView"))
            leafNodes.add(new Pair<>(node, longClickable));

        for (BasicTreeNode leafNode : node.getChildren()) {
            if (((UiNode)leafNode).getAttribute("long-clickable").equals("true")) longClickable = true;

            findLeafNodes((UiNode) leafNode, leafNodes, longClickable);
        }
    }

    private void findWebkitAncestors(UiNode node, List<UiNode> webkitNodes) {
        while (node != null) {
            if (node.getAttribute("class") != null && node.getAttribute("class").equals("android.webkit.WebView")) webkitNodes.add(node);

            node = (UiNode) node.getParent();
        }
    }

    private List<Pair<Event, List<Double>>> findInteractables(UiNode root, List<Pair<Event, List<Double>>> interactables, boolean navigateUp) {
        if (root == null)   return new ArrayList<Pair<Event, List<Double>>>();

        BasicTreeNode[] nodes = root.getChildren();
        for (BasicTreeNode node1 : nodes) {
            UiNode node = (UiNode) node1;

            String id = node.getAttribute("resource-id");
            String type = node.getAttribute("class");
            String clickable = node.getAttribute("clickable");
            String long_clickable = node.getAttribute("long-clickable");
            String checkable = node.getAttribute("checkable");
            String content_desc = node.getAttribute("content-desc");

            if (id.contains("com.android.systemui") || id.contains("statusBarBackground") || id.contains("navigationBarBackground"))    continue;

            Event testRecorderEvent = null;
            if (type.contains("EditText")) testRecorderEvent = new Event("VIEW_TEXT_CHANGED", node, "", "0");
            else if ((type.equals("android.support.v7.widget.RecyclerView") || type.equals("android.widget.ListView") || type.equals("android.widget.ExpandableListView")
                    || type.contains("RelativeLayout") || type.contains("LinearLayout") || type.contains("FrameLayout") || type.contains("Spinner")) && node.getChildCount() > 0) {
                List<Pair<UiNode, Boolean>> leafNodes = new ArrayList<>();
                findLeafNodes(node, leafNodes, false);

                for (Pair<UiNode, Boolean> leaf : leafNodes) {
                    if (type.equals("android.support.v7.widget.RecyclerView") || type.equals("android.widget.ListView")) {
                        if (!leaf.second) {
                            testRecorderEvent = new Event("LIST_ITEM_CLICKED", leaf.first, "", "0");
                        } else {
                            testRecorderEvent = new Event("LIST_ITEM_CLICKED/VIEW_LONG_CLICKED", leaf.first, "", "0");
                        }
                    } else {
                        if (clickable.equals("true") && long_clickable.equals("true"))
                            testRecorderEvent = new Event("VIEW_LONG_CLICKED/VIEW_CLICKED", leaf.first, "", "0");
                        else if (clickable.equals("true"))
                            testRecorderEvent = new Event("VIEW_CLICKED", leaf.first, "", "0");
                        else if (long_clickable.equals("true"))
                            testRecorderEvent = new Event("VIEW_LONG_CLICKED", leaf.first, "", "0");
                        else if (leaf.first.getAttribute("long-clickable").equals("true"))
                            testRecorderEvent = new Event("VIEW_LONG_CLICKED", leaf.first, "", "0");
                        else if (leaf.first.getAttribute("clickable").equals("true"))
                            testRecorderEvent = new Event("VIEW_CLICKED", leaf.first, "", "0");
                        else testRecorderEvent = new Event("NONE", leaf.first, "", "0");
                    }

                    if (/*testRecorderEvent != null &&*/!alreadyContainsClickable(testRecorderEvent, interactables)) {
                        List<UiNode> webkitNodes = new ArrayList<>();
                        findWebkitAncestors(testRecorderEvent.getTargetElement(), webkitNodes);
                        if (webkitNodes.size() < 1) {
                            List<Double> indexes = new ArrayList<>();
                            interactables.add(new Pair<>(testRecorderEvent, indexes));
                        }
                    }
                }
            } else {
                if (long_clickable.equals("true") && clickable.equals("true"))
                    testRecorderEvent = new Event("VIEW_LONG_CLICKED/VIEW_CLICKED", node, "", "0");
                else if (long_clickable.equals("true") && clickable.equals("false"))
                    testRecorderEvent = new Event("VIEW_LONG_CLICKED", node, "", "0");
                else if (long_clickable.equals("false") && clickable.equals("true"))
                    testRecorderEvent = new Event("VIEW_CLICKED", node, "", "0");
                else if (checkable.equals("true"))
                    testRecorderEvent = new Event("VIEW_LONG_CLICKED", node, "", "0");
                else testRecorderEvent = new Event("NONE", node, "", "0");
            }

            if (testRecorderEvent != null && !alreadyContainsClickable(testRecorderEvent, interactables)) {
                List<UiNode> webkitNodes = new ArrayList<>();
                findWebkitAncestors(testRecorderEvent.getTargetElement(), webkitNodes);
                if (webkitNodes.size() < 1) {
                    List<Double> indexes = new ArrayList<>();
                    switch (content_desc) {
                        case "Navigate up":
                            interactables.add(0, new Pair<>(testRecorderEvent, indexes));

                            navigateUp = true;
                            break;
                        case "More options":
                            if (navigateUp)
                                interactables.add(1, new Pair<>(testRecorderEvent, indexes));
                            else
                                interactables.add(0, new Pair<>(testRecorderEvent, indexes));
                            break;
                        default:
                            interactables.add(new Pair<>(testRecorderEvent, indexes));
                            break;
                    }
                }
            }

            findInteractables(node, interactables, navigateUp);
        }

        return interactables;
    }

    private boolean alreadyContainsClickable(Event event, List<Pair<Event, List<Double>>> clickables) {
        for (Pair<Event, List<Double>> c : clickables) {
            UiNode targetElement = c.first.getTargetElement();
            UiNode eventTargetElement = event.getTargetElement();
            if (targetElement == null || eventTargetElement == null)  continue;
            if (targetElement.toString().equals(eventTargetElement.toString())) return true;
        }

        return false;
    }

    private static String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    private void findUINodesByID(UiNode node, String resourceId, int position) {
        if (node == null)   return;

        String resource_id = node.getAttribute("resource-id");
        if (resource_id != null && !resource_id.equals("")) {
            resource_id = resource_id.split("/")[1];
        }

        if (resource_id != null && !resource_id.contains("com.android.systemui") &&
                !resource_id.contains("statusBarBackground") &&
                !resource_id.contains("navigationBarBackground") &&
                resource_id.equals(resourceId)) {
            String type = node.getAttribute("class");
            if (type.equals("android.support.v7.widget.RecyclerView") || type.contains("RelativeLayout") ||
                    type.contains("LinearLayout") || type.contains("FrameLayout")) {

                if (position != -1 && node.getChildrenList().size() >= position) {
                    List<Pair<UiNode, Boolean>> leafs = new ArrayList<>();
                    findLeafNodes((UiNode) node.getChildrenList().get(position), leafs, false);
                    for (int i = 0; i < leafs.size(); i++) nodes.add(leafs.get(i).first);
                }
                else {
                    List<Pair<UiNode, Boolean>> leafs = new ArrayList<>();
                    findLeafNodes(node, leafs, false);
                    for (int i = 0; i < leafs.size(); i++) nodes.add(leafs.get(i).first);
                }
            } else {
                nodes.add(node);
            }
        }

        BasicTreeNode[] nodes = node.getChildren();
        for (BasicTreeNode node1 : nodes) {
            UiNode childNode = (UiNode) node1;
            findUINodesByID(childNode, resourceId, position);
        }
    }

    private void findUINodesByText(UiNode node, String text) {
        if (node == null)   return;

        String node_text = node.getAttribute("text");

        if (node_text.equals(text)) {
            nodes.add(node);
        }

        BasicTreeNode[] nodes = node.getChildren();
        for (BasicTreeNode node1 : nodes) {
            UiNode childNode = (UiNode) node1;
            findUINodesByText(childNode, text);
        }
    }

    private void findAllNodes(UiNode node, List<UiNode> allNodes) {
        allNodes.add(node);

        BasicTreeNode[] children = node.getChildren();
        for (BasicTreeNode aChildren : children) {
            UiNode childNode = (UiNode) aChildren;
            findAllNodes(childNode, allNodes);
        }
    }

    private Class determineClass(Bundle b) {
        Class c = null;

        try {
            String s = b.getString("arg");
            c = Class.forName(s);
        } catch (ClassNotFoundException e) {
            Log.d(LOG_TAG, "ClassNotFoundException: " + e.getMessage());
        }

        return c;
    }

    private String determineModuleName(Bundle b) {
        String module = b.getString("module");

        if (module == null)
            throw new RuntimeException("Please provide the module name as an argument.");

        return module;
    }

    private String determineScenarioName(Bundle b) {
        String scenario = b.getString("scenario");

        if (scenario == null)
            throw new RuntimeException("Please provide the scenario name as an argument.");

        return scenario;
    }

    private String determineMode(Bundle b) {
        String mode = b.getString("mode");

        if (mode == null)
            throw new RuntimeException("Please provide the mode as an argument.");

        return mode;
    }

    private void getSourceAppAssertions() throws Exception {
        InputStream scenariosInputStream = InstrumentationRegistry.getTargetContext().getResources().getAssets().open("source-scenarios/" + scenarioName);
        scenario = new FiniteStateMachine();

        if (scenariosInputStream != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(scenariosInputStream, "UTF8"))) {
                for (String line; (line = br.readLine()) != null; ) {
                    if (line.startsWith("Assertion:")) {
                        Pattern p = Pattern.compile("\\[(.*?)\\]");
                        Matcher m = p.matcher(line);
                        int count = 0;
                        UiNode targetElement = null;
                        String assertionMethod = null, fileName = null;
                        List<AssertionMatcher> assertionMatchers = new ArrayList<AssertionMatcher>();
                        while(m.find()) {
                            if (count == 0) {
                                fileName = m.group(1);
                                targetElement = findTargetElement(m.group(1));
                            } else if (count == 1) {
                                assertionMethod = m.group(1);
                            } else {
                                String hierarchyMethod = null, method = null;
                                UiNode parameter = null;
                                String[] matchersSplittedByDash = m.group(1).split("-");
                                if (matchersSplittedByDash.length > 0) {
                                    if (matchersSplittedByDash[0].equals("withParent") || matchersSplittedByDash[0].equals("withChild")
                                            || matchersSplittedByDash[0].equals("hasDescendant") || matchersSplittedByDash[0].equals("isDescendantOfA")
                                            || matchersSplittedByDash[0].equals("hasSibling")) {
                                        hierarchyMethod = matchersSplittedByDash[0];
                                        if (matchersSplittedByDash.length > 1) method = matchersSplittedByDash[1];
                                        if (matchersSplittedByDash.length > 2) {
                                            if (method != null && method.equals("withText")) {
                                                String text = matchersSplittedByDash[2];
                                                parameter = findTargetElementWithText(fileName, text);
                                            } else {
                                                String parameterFileName = matchersSplittedByDash[2];
                                                parameter = findTargetElement(parameterFileName);
                                            }
                                        }
                                    } else {
                                        method = matchersSplittedByDash[0];
                                        if (matchersSplittedByDash.length > 1) {
                                            if (method != null && method.equals("withText")) {
                                                String text = m.group(1).replace("withText-", "");
                                                parameter = findTargetElementWithText(fileName, text);
                                            } else if (method != null && method.equals("isDisplayingAtLeast")) {
                                                method = "isCompletelyDisplayed";
                                            } else {
                                                String parameterFileName = matchersSplittedByDash[1];
                                                parameter = findTargetElement(parameterFileName);
                                            }
                                        }
                                    }
                                }
                                assertionMatchers.add(new AssertionMatcher(hierarchyMethod, method, parameter));
                            }
                            count++;
                        }
                        assertions.add(new Assertion(targetElement, assertionMethod, assertionMatchers));
                    }
                }
            }
        }
    }

    private UiNode findTargetElement(String fileName) throws Exception {
        UiNode targetElement = null;
        InputStream guiHierarchyInputStream = InstrumentationRegistry.getTargetContext().getResources().getAssets().open("UIAutomator-UIHierarchies/" + fileName + ".xml");
        UiNode guiHierarchyRoot = (UiNode) new UiHierarchyXmlLoader().parseXml(convertStreamToString(guiHierarchyInputStream));
        if (guiHierarchyRoot != null && guiHierarchyRoot.getChildCount() > 0)
            guiHierarchyRoot = (UiNode) guiHierarchyRoot.getChildren()[guiHierarchyRoot.getChildCount() - 1];

        updateCheckBoxText(guiHierarchyRoot);
        String[] targetElementSplittedBySpace = fileName.split(" ");
        String id = targetElementSplittedBySpace[0];

        nodes = new ArrayList<>();
        findUINodesByID(guiHierarchyRoot, id, -1);

        if (nodes.size() > 1) {
            targetElement = findNode(fileName, guiHierarchyRoot);
        } else if (nodes.size() == 1) {
            targetElement = nodes.get(0);
        }

        if (targetElement == null) {
            targetElement = findNode(fileName, guiHierarchyRoot);
        }

        if (targetElement == null && nodes.size() > 1) targetElement = nodes.get(0);

        return targetElement;
    }

    private UiNode findTargetElementWithText(String fileName, String text) throws Exception {
        UiNode targetElement = findTargetElement(fileName, text);

        if (targetElement == null) {
            String[] fileNameFromSplitted = fileName.split(" ");
            if (fileNameFromSplitted.length == 5) {
                fileName = fileName + " 1";
            } else if (fileNameFromSplitted.length == 6) {
                int postfix = Integer.parseInt(fileNameFromSplitted[5]);
                postfix++;
                StringBuilder fileNameFromSB = new StringBuilder();
                for (int i = 0; i < fileNameFromSplitted.length - 1; i++) {
                    if (i != 0) fileNameFromSB.append(" ").append(fileNameFromSplitted[i]);
                    else fileNameFromSB.append(fileNameFromSplitted[i]);
                }
                fileName = fileNameFromSB.toString() + " " + postfix;
            }
            targetElement = findTargetElement(fileName, text);
        }

        return targetElement;
    }

    private UiNode findTargetElement(String fileName, String text) throws Exception {
        UiNode targetElement = null;
        InputStream guiHierarchyInputStream = InstrumentationRegistry.getTargetContext().getResources().getAssets().open("UIAutomator-UIHierarchies/" + fileName + ".xml");
        UiNode guiHierarchyRoot = (UiNode) new UiHierarchyXmlLoader().parseXml(convertStreamToString(guiHierarchyInputStream));
        if (guiHierarchyRoot != null && guiHierarchyRoot.getChildCount() > 0)
            guiHierarchyRoot = (UiNode) guiHierarchyRoot.getChildren()[guiHierarchyRoot.getChildCount() - 1];

        nodes = new ArrayList<>();
        findUINodesByText(guiHierarchyRoot, text);

        if (nodes.size() > 1) {
            targetElement = findNode(fileName, guiHierarchyRoot);
        } else if (nodes.size() == 1) {
            targetElement = nodes.get(0);
        }
        return targetElement;
    }

    private UiNode findNode(String fileNameFrom, UiNode rootFrom) throws Exception {
        List<String> matches = getTextOfNode(fileNameFrom);

        UiNode node = null;
        StringBuilder text = new StringBuilder();
        List<UiNode> allNodes = new ArrayList<>();
        findAllNodes(rootFrom, allNodes);

        for (String match : matches) {
            if (match != null && match.length() > 0) {
                for (UiNode n : allNodes) {
                    String textOrDesc = n.getAttribute("text");
                    if(textOrDesc == null || textOrDesc.equals(""))  textOrDesc = n.getAttribute("content-desc");
                    if (textOrDesc != null && !textOrDesc.equals("") && textOrDesc.equalsIgnoreCase(match)) {
                        node = n;
                        text.append(" ").append(textOrDesc);
                    }
                }
            }
        }

        if (node != null && text.length() > 0)  node.addAtrribute("text", text.toString());

        if (node == null) {
            String clazz = getClassOfNode(fileNameFrom);
            if (clazz.length() > 0) {
                int count = 0;
                for (UiNode n : nodes) {
                    String c = n.getAttribute("class");
                    if (c != null && c.toLowerCase().contains(clazz.toLowerCase())) {
                        String textOrDesc = n.getAttribute("text");
                        if(textOrDesc == null || textOrDesc.equals(""))  textOrDesc = n.getAttribute("content-desc");
                        text.append(" ").append(textOrDesc);
                        node = n;

                        count++;

                    }
                }

                if (count != 1) node = null;
                else if (text.length() > 0)  node.addAtrribute("text", text.toString());
            }
        }

        return node;
    }

    private List<String> getTextOfNode(String fileNameFrom) throws Exception {
        InputStream inputStream = InstrumentationRegistry.getTargetContext().getResources().getAssets().open("Espresso-UIHierarchies/" + fileNameFrom + ".xml");
        String str = convertStreamToString(inputStream);

        int beginningIndex = -1;
        if (str.indexOf("text=") > 0)   beginningIndex = str.indexOf(", text=") + 7;
        else if (str.indexOf("desc=") > 0)  beginningIndex = str.indexOf(", desc=") + 7;

        List<String> matches = new ArrayList<>();
        if (beginningIndex > 0) {
            int endIndex = str.indexOf(",", beginningIndex);
            if (!str.substring(beginningIndex, endIndex).equals("")) {
                matches.add(str.substring(beginningIndex, endIndex));
            }

            while (beginningIndex >= 0) {
                beginningIndex = str.indexOf(", text=", beginningIndex + 7);
                endIndex = str.indexOf(",", beginningIndex + 7);
                if (beginningIndex != -1 && !str.substring(beginningIndex + 7, endIndex).equals("")) {
                    matches.add(str.substring(beginningIndex + 7, endIndex));
                }
            }
        }
        return matches;
    }

    private String getClassOfNode(String fileNameFrom) throws Exception {
        InputStream inputStream = InstrumentationRegistry.getTargetContext().getResources().getAssets().open("Espresso-UIHierarchies/" + fileNameFrom + ".xml");
        String str = convertStreamToString(inputStream);

        int beginningIndex = str.indexOf("+>") + 2;
        int endIndex = str.indexOf("{", beginningIndex);

        return str.substring(beginningIndex, endIndex);
    }

    private void updateCheckBoxText(UiNode root) {
        BasicTreeNode[] nodes = root.getChildren();

        for (int index = 0; index < nodes.length; index++) {
            UiNode node = (UiNode) nodes[index];

            String clazz = node.getAttribute("class");
            if (clazz.contains("CheckBox") || clazz.contains("Switch")) {
                StringBuilder textOrDesc = new StringBuilder();
                while (index + 1 < nodes.length) {
                    index++;

                    UiNode nextNode = (UiNode) nodes[index];
                    if (nextNode.getAttribute("text") != null)
                        textOrDesc.append(nextNode.getAttribute("text")).append(" ");
                    else if (nextNode.getAttribute("content-desc") != null)
                        textOrDesc.append(nextNode.getAttribute("content-desc")).append(" ");
                }
                int idx = index;
                while (idx - 1 >= 0) {
                    idx--;

                    UiNode prevNode = (UiNode) nodes[idx];
                    if (prevNode.getAttribute("text") != null)
                        textOrDesc.append(prevNode.getAttribute("text")).append(" ");
                    else if (prevNode.getAttribute("content-desc") != null)
                        textOrDesc.append(prevNode.getAttribute("content-desc")).append(" ");
                }
                node.addAtrribute("text", textOrDesc.toString());
            }

            updateCheckBoxText(node);
        }
    }

    private String getEditTextLabel(UiNode rootFrom, String xpath, UiNode node, Boolean textAlreadyExists) {
        List<UiNode> neighbourNodes = new ArrayList<>();
        findNeighbourNodes(rootFrom, neighbourNodes);

        int node_y1 = Integer.parseInt(node.getAttribute("y1"));
        int node_x1 = Integer.parseInt(node.getAttribute("x1"));
        int node_x2 = Integer.parseInt(node.getAttribute("x2"));

        for (int i = 0; i < neighbourNodes.size(); i++) {
            StringBuilder xp = new StringBuilder();
            findXPath(xp, neighbourNodes.get(i));

            if (xp.toString().equals(xpath)) {
                int index = i;

                i--;
                while (i >= 0) {
                    String label = null;

                    UiNode node_i = neighbourNodes.get(i);
                    String class_attribute = node_i.getAttribute("class");
                    if (!class_attribute.contains("Button") && !class_attribute.contains("EditText")) {
                        if (node_i.getAttribute("src") == null || node_i.getAttribute("src").equals("")) {
                            int node_i_y1 = Integer.parseInt(node_i.getAttribute("y1"));
                            int node_i_x1 = Integer.parseInt(node_i.getAttribute("x1"));
                            int node_i_x2 = Integer.parseInt(node_i.getAttribute("x2"));

                            if (node_x1 < node_i_x1)    node_x1 = node_x2;
                            else if (node_i_x1 < node_x1)   node_i_x1 = node_i_x2;

                            double diff_y1 = Math.abs(node_y1 - node_i_y1);
                            double diff_x1 = Math.abs(node_x1 - node_i_x1);
                            if (textAlreadyExists) {
                                if (diff_y1 >= 0 && diff_y1 < 150 && diff_x1 < 150) {
                                    label = node_i.getAttribute("text");
                                    if (label == null || label.trim().equals(""))  label = node_i.getAttribute("content-desc");
                                }
                            } else {
                                if (diff_y1 >= 0 && diff_y1 < 250 && diff_x1 < 250) {
                                    label = node_i.getAttribute("text");
                                    if (label == null || label.trim().equals(""))  label = node_i.getAttribute("content-desc");
                                }
                            }
                        }

                        if (label != null && !label.equals("")) return label;
                    }
                    i--;
                }

                i = index;
            }
        }

        return "";
    }

    private void findNeighbourNodes(UiNode root, List<UiNode> neighbourNodes) {
        BasicTreeNode[] nodes = root.getChildren();

        for (BasicTreeNode node1 : nodes) {
            UiNode node = (UiNode) node1;

            String type = node.getAttribute("class");
            if ((node.getAttribute("text") != null && !node.getAttribute("text").equals(""))
                    || (node.getAttribute("content-desc") != null && !node.getAttribute("content-desc").equals(""))
                    || type.contains("EditText"))
                neighbourNodes.add(node);

            findNeighbourNodes(node, neighbourNodes);
        }
    }
}
