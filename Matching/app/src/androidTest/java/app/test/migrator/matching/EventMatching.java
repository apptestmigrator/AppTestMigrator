package app.test.migrator.matching;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.app.Instrumentation;

import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.test.espresso.NoActivityResumedException;
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
import java.util.Hashtable;
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

import org.hamcrest.CoreMatchers;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

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
import static android.support.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static android.support.test.espresso.intent.Intents.intending;
import static android.support.test.espresso.intent.matcher.IntentMatchers.isInternal;
import static android.support.test.espresso.matcher.ViewMatchers.withContentDescription;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static android.support.test.espresso.matcher.ViewMatchers.withXPath;
import static android.support.test.runner.lifecycle.Stage.RESUMED;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.Matchers.allOf;

import okhttp3.RequestBody;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.Route;

@RunWith(AndroidJUnit4.class)
public class EventMatching {
    private Bundle b = InstrumentationRegistry.getArguments();
    private Class cl = determineClass(b);
    private String module = determineModuleName(b);
    private String scenarioName = determineScenarioName(b);
    private String mode = determineMode(b);
    private final String LOG_TAG = InstrumentationRegistry.getTargetContext().getPackageName();
    private boolean navigationDrawerIsOpen = false ;
    private FiniteStateMachine scenario = new FiniteStateMachine();
    private List<UiNode> nodes = new ArrayList<UiNode>();
    private Map<String, Double> dictionary = new HashMap<String, Double>();
    private Map<String, String> id_image = new HashMap<String, String>();
    private Map<String, String> id_inputType = new HashMap<String, String>();
    private int randomNum = 0;
    private Map<Transition, List<Event>> matchedEvents_map = new LinkedHashMap<>();
    private List<Triple<String, State, Event>> targetEvents = new ArrayList<Triple<String, State, Event>>();
    private boolean terminate = false;
    private OkHttpClient client = new OkHttpClient.Builder().authenticator(new Authenticator() {
        public Request authenticate(@NonNull Route route, @NonNull Response response) throws IOException {
            String credential = Credentials.basic("neo4j", "neo4j");
            return response.request().newBuilder().header("Authorization", credential).build();
        }
    }).build();
    private String staticNodeType = "ACT";
    private LemmatizationAndPOSTagger lemmatizationAndPOSTagger = new LemmatizationAndPOSTagger();

    @Rule
    public ActivityTestRule activityRule = new ActivityTestRule(cl);

    @Before
    public void setUp() throws Exception {
        Intents.init();
        intending(CoreMatchers.not(isInternal())).respondWith(new Instrumentation.ActivityResult(0, null));

        getSourceAppScenarios();

        createIdImageDict("image_dict", true);
        createIdImageDict("inputType_dict", false);
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
            removeExtraRandomActions();

            Writer out = createWriter(true);
            for (Triple<String, State, Event> targetEvent : targetEvents) {
                out.write(targetEvent.first);
            }
            out.close();
        }
        Intents.release();
    }

    private void removeExtraRandomActions() {
        List<Integer> indexOfNonRandomEvents = new ArrayList<Integer>();
        for (int i = 0; i < targetEvents.size(); i++) {
            if (targetEvents.get(i).first.contains("~RANDOM")) {
                indexOfNonRandomEvents.add(i);
            }
        }

        if (indexOfNonRandomEvents.size() > 0 && indexOfNonRandomEvents.get(0) != 0) {
            indexOfNonRandomEvents.add(0, 0);
        }

        boolean found = false;
        List<Pair<Integer, Integer>> indexesToRemove = new ArrayList<Pair<Integer, Integer>>();

        for (int i = 0; i < indexOfNonRandomEvents.size() - 1; i++) {
            int startIndex = indexOfNonRandomEvents.get(i);
            int endIndex = indexOfNonRandomEvents.get(i + 1);
            if (endIndex - startIndex > 1) {
                boolean safeToProceed = false;
                for (int j = startIndex + 1; j < endIndex; j++) {
                    if (!targetEvents.get(j).first.contains("replaceText")) {
                        safeToProceed = true;
                        break;
                    }
                }

                if (safeToProceed) {
                    for (int j = startIndex; j < endIndex; j++) {
                        for (int k = endIndex; k > startIndex + 1; k--) {
                            State state1 = targetEvents.get(j).second;
                            State state2 = targetEvents.get(k - 1).second;
                            Event event = targetEvents.get(k).third;

                            if (state1 == null || state2 == null || event == null || state1.computeCosineSimilarity(state2) < 1)
                                continue;

                            found = false;
                            for (Pair<Event, List<Double>> actionable : state1.getActionables()) {
                                if (actionable.first.toString().equals(event.toString())) {
                                    indexesToRemove.add(new Pair<Integer, Integer>(j + 1, k));

                                    found = true;

                                    break;
                                }
                            }

                            if (found) break;
                        }
                        if (found) break;
                    }
                    if (found) break;
                }
            }
        }

        int deduction = 0;
        for (Pair<Integer, Integer> indexToRemove : indexesToRemove) {
            int beginningIndex = indexToRemove.first - deduction;
            int endIndex = indexToRemove.second - deduction;

            while(targetEvents.get(beginningIndex).first.contains("replaceText"))  beginningIndex++;

            if (targetEvents.get(beginningIndex).first.equals(targetEvents.get(endIndex).first))  continue;

            if (endIndex - beginningIndex > 0) {
                targetEvents.subList(beginningIndex, endIndex).clear();

                deduction += endIndex - beginningIndex;
            }
        }
    }

    @Test
    public void test() {
        if (!mode.equals("EventMatching"))  terminate = true;
        else {
            findPreviouslyMigratedEvents();

            int nextIndex = findIndexOfNextEventToMigrate();

            Map<Integer, List<Triple<String, State, Event>>> targetEvents_map = new LinkedHashMap<Integer, List<Triple<String, State, Event>>>();
            FiniteStateMachine fsm = new FiniteStateMachine();
            State currState, prevState = null;
            Event prevEvent = null, nextEvent;
            Boolean dynamicMatch = false;
            for (int index = nextIndex; index < scenario.getTransitions().size(); index++) {
                Transition transition = scenario.getTransitions().get(index);
                Event event = transition.getLabel().first;
                boolean matched = false;
                long startTimeMillis = SystemClock.uptimeMillis(), TIMEOUT = 360000;
                List<Triple<String, State, Event>> targetEvents_temp = new ArrayList<Triple<String, State, Event>>();
                while (SystemClock.uptimeMillis() - startTimeMillis <= TIMEOUT) {
                    nextEvent = null;
                    long diff = SystemClock.uptimeMillis() - startTimeMillis;
                    currState = getCurrentState(fsm);
                    if (event.getType().equals("PRESS_BACK")) {
                        try {
                            Espresso.pressBack();
                            targetEvents_temp.add(new Triple<String, State, Event>("\npressBack();~RANDOM", getCurrentState(fsm), null));
                            matched = true;
                        } catch (RuntimeException e) {
                            e.printStackTrace();
                            Log.e("NoActivityResumedException", e.getMessage());
                        }
                    } else {
                        if (event.getTargetElement().getAttribute("class") != null && event.getTargetElement().getAttribute("class").contains("EditText")) {
                            matched = performReplaceText(currState, getRoot(), transition, targetEvents_temp);
                            if (matched) break;
                        } else {
                            Pair<Event, Boolean> ne = findNextEvent(transition, currState);
                            if (ne != null) {
                                nextEvent = ne.first;
                                dynamicMatch = ne.second;
                            }
                        }
                        String random = "~RANDOM";
                        if (nextEvent == null) {
                            random = "RANDOM";
                            nextEvent = pickRandomAction(currState, prevEvent, prevState);
                            randomNum++;
                        } else {
                            matched = true;
                        }

                        if (nextEvent == null) {
                            try {
                                prevState = currState;
                                Espresso.pressBack();
                                currState = getCurrentState(fsm);
                                addStateAndTransitionToFSM(fsm, currState, prevState, new Event("PRESS_BACK", null, "", ""));
                                targetEvents_temp.add(new Triple<String, State, Event>("\npressBack();RANDOM", getCurrentState(fsm), null));
                                continue;
                            } catch (RuntimeException e) {
                                e.printStackTrace();
                                Log.e("NoActivityResumedException", e.getMessage());
                                break;
                            }
                        }

                        prevState = currState;
                        try {
                            prepareAction(nextEvent, transition, random, targetEvents_temp, fsm);
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.e("Unable to perform action", e.getMessage());
                        }

                        addStateAndTransitionToFSM(fsm, currState, prevState, nextEvent);

                        UiNode nextEventTargetElement = nextEvent.getTargetElement();
                        String id = "0";
                        if (nextEventTargetElement != null && nextEventTargetElement.getAttribute("resource-id") != null && !nextEventTargetElement.getAttribute("resource-id").equals("")) {
                            id = nextEventTargetElement.getAttribute("resource-id").split("/")[1];
                        }
                        if (nextEventTargetElement != null)
                            dumpUIHierarchy(id + " " + nextEventTargetElement.getAttribute("x") + " " + nextEventTargetElement.getAttribute("y") + " " + nextEventTargetElement.getAttribute("width") + " " + nextEventTargetElement.getAttribute("height"), "TargetAppUIHierarchies");

                        prevEvent = nextEvent;
                    }
                    if (matched && dynamicMatch) {
                        break;
                    }
                }

                if (!matched) {
                    Transition lastMatchedTransition = null;
                    for (Transition transition1 : matchedEvents_map.keySet()) {
                        lastMatchedTransition = transition1;
                    }
                    if (lastMatchedTransition != null) {
                        List<Event> matchedEvents = matchedEvents_map.get(lastMatchedTransition);
                        if (matchedEvents.size() > 0) {
                            nextEvent = matchedEvents.get(0);
                            matchedEvents.remove(0);
                            targetEvents_temp = setNewTargetEvents(targetEvents_map, nextEvent, index, transition, false);
                            index--;
                            if (index > 0) {
                                List<Triple<String, State, Event>> targetEvents = targetEvents_map.get(index);
                                targetEvents.remove(targetEvents.size() - 1);
                                targetEvents.addAll(targetEvents_temp);
                                targetEvents_map.put(index, targetEvents);
                            }
                            break;
                        } else {
                            if (index < scenario.getTransitions().size()) {
                                targetEvents_temp = setNewTargetEvents(targetEvents_map, null, index, transition, true);
                                targetEvents_map.put(index, targetEvents_temp);
                            }
                            break;
                        }
                    } else {
                        if (index < scenario.getTransitions().size()) {
                            targetEvents_temp = setNewTargetEvents(targetEvents_map, null, index, transition, true);
                            targetEvents_map.put(index, targetEvents_temp);
                        }
                        break;
                    }
                } else {
                    targetEvents_map.put(index, targetEvents_temp);
                }
            }

            for (int index : targetEvents_map.keySet()) {
                targetEvents.addAll(targetEvents_map.get(index));
            }
        }
    }

    private int findIndexOfNextEventToMigrate() {
        int index = 0;
        InputStream targetTestInputStream;
        try {
            List<String> updatedFileContent = new ArrayList<String>();

            targetTestInputStream = InstrumentationRegistry.getTargetContext().getResources().getAssets().open("target-tests/AppTestMigrator_" + scenarioName + ".txt");
            if (targetTestInputStream != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(targetTestInputStream, "UTF8"));
                for (String line; (line = br.readLine()) != null; ) {
                    if (line.contains("nextEvent:")) {
                        index = Integer.parseInt(line.substring(line.indexOf("index=\"") + 7, line.indexOf("\"", line.indexOf("index=\"") + 7)));
                    } else if(!line.trim().equals("")) updatedFileContent.add(line);
                }
            }

            Writer out = createWriter(false);
            for (String c : updatedFileContent) {
                triggerPreviouslyMigratedEvents(c);
                out.write("\n" + c);
            }
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("IOException", e.getMessage());
        }

        return index;
    }

    @NonNull
    private Writer createWriter(boolean append) throws IOException {
        String path = Environment.getExternalStorageDirectory().getAbsolutePath();
        File dir = new File(path + "/target-tests/");
        if (!dir.exists()) dir.mkdir();
        File file = new File(dir, "AppTestMigrator_" + scenarioName + ".txt");
        if (append) return new PrintWriter(new FileWriter(file, true));
        else return new PrintWriter(new FileWriter(file, false));
    }

    private List<Triple<String, State, Event>> setNewTargetEvents(Map<Integer, List<Triple<String, State, Event>>> targetEvents_map, Event nextEvent, int index, Transition transition, boolean skip) {
        List<Triple<String, State, Event>> newTargetEvents = new ArrayList<Triple<String, State, Event>>();

        List<Triple<String, State, Event>> lastMatchTargetEvents = targetEvents_map.get(index);
        if (!skip && lastMatchTargetEvents != null && lastMatchTargetEvents.size() > 0) {
            lastMatchTargetEvents.remove(lastMatchTargetEvents.size() - 1);
            newTargetEvents.addAll(lastMatchTargetEvents);
        }

        if (skip) {
            index++;
            String idx = Integer.toString(index);
            newTargetEvents.add(new Triple<String, State, Event>("\nnextEvent: index=\"" + idx + "\" ", null, null));
        } else {
            String idx = Integer.toString(index);
            StringBuilder xpath = new StringBuilder();
            findXPath(xpath, nextEvent.getTargetElement());
            newTargetEvents.add(new Triple<String, State, Event>("\nonView(withXPath(\"" + xpath + "\")).perform(click());~RANDOM", null, null));
            newTargetEvents.add(new Triple<String, State, Event>("\nnextEvent: index=\"" + idx + "\" ", null, null));
        }

        return newTargetEvents;
    }

    private Pair<Event, Boolean> findNextEvent(Transition transition, State currState) {
        Event nextEvent;
        Event event = transition.getLabel().first;
        double max_score = 0;
        List<Event> matchedEvents = new ArrayList<Event>();
        for (Pair<Event, List<Double>> clickableNode : currState.getActionables()) {
            UiNode stateNodeTargetElement = clickableNode.first.getTargetElement();
            String stateNodeClass = stateNodeTargetElement.getAttribute("class");

            if (stateNodeClass.contains("EditText")) continue;

            String stateNodeTargetElementText = findTargetElementText(stateNodeTargetElement);
            String stateNodeTargetElementId = findStateNodeTargetElementId(stateNodeTargetElement);
            double matchingScore = findMatchedEvents(event, stateNodeTargetElementText, stateNodeTargetElementId);
            if (matchingScore >= 0.4 && matchingScore >= max_score) {
                if (matchingScore > max_score) {
                    matchedEvents.clear();
                    matchedEvents.add(clickableNode.first);
                } else {
                    matchedEvents.add(clickableNode.first);
                }
                max_score = matchingScore;
            }
        }

        double static_max_score = 0;
        List<Triple<JsonNode, JsonRel, JsonNode>> staticMatchedEvents = new ArrayList<Triple<JsonNode, JsonRel, JsonNode>>();
        if (matchedEvents.size() < 1) {
            for (Triple<JsonNode, JsonRel, JsonNode> staticNodeRel : createStaticNodesAndRels()) {
                Boolean alreadyVisited = false;
                for (Pair<Event, List<Double>> sNode : currState.getActionables()) {
                    UiNode sNodeTargetElement = sNode.first.getTargetElement();
                    String resource_id = "", textOrDesc = "";
                    if (sNodeTargetElement.getAttribute("resource-id") != null && sNodeTargetElement.getAttribute("resource-id").contains("/"))
                        resource_id = sNodeTargetElement.getAttribute("resource-id").split("/")[1];
                    if (sNodeTargetElement.getAttribute("text") != null)
                        textOrDesc = sNodeTargetElement.getAttribute("text");
                    if (textOrDesc.equals("") && sNodeTargetElement.getAttribute("content-desc") != null)
                        textOrDesc = sNodeTargetElement.getAttribute("content-desc");
                    if(resource_id.equals(staticNodeRel.second.getId()) &&
                            textOrDesc.equals(staticNodeRel.second.getText()))
                        alreadyVisited = true;
                }

                if (alreadyVisited) continue;

                String stateNodeTargetElementText = staticNodeRel.second.getText();
                String stateNodeTargetElementId = staticNodeRel.second.getId();
                double matchingScore = findMatchedEvents(event, stateNodeTargetElementText, stateNodeTargetElementId);
                if (matchingScore >= 0.4 && matchingScore >= static_max_score) {
                    if (matchingScore > static_max_score) {
                        staticMatchedEvents.clear();
                        staticMatchedEvents.add(staticNodeRel);
                    } else {
                        staticMatchedEvents.add(staticNodeRel);
                    }
                    static_max_score = matchingScore;
                }
            }

            if (staticMatchedEvents.size() > 0) {
                for (Triple<JsonNode, JsonRel, JsonNode> staticMatchedEvent : staticMatchedEvents) {
                    nextEvent = findStaticNextEvent(currState, staticMatchedEvent);
                    if (nextEvent != null) {
                        return new Pair<Event, Boolean>(nextEvent, false);
                    }
                }
            }
        } else {
            matchedEvents_map.put(transition, matchedEvents);
            nextEvent = matchedEvents.get(0);
            matchedEvents.remove(0);
            return new Pair<Event, Boolean>(nextEvent, true);
        }

        return null;
    }

    private double findMatchedEvents(Event event, String stateNodeTargetElementText, String stateNodeTargetElementId) {
        String eventTargetElementText = findTargetElementText(event.getTargetElement());

        String eventTargetElementId = event.getTargetElement().getAttribute("resource-id");
        if (eventTargetElementId != null && eventTargetElementId.contains("/"))
            eventTargetElementId = eventTargetElementId.split("/")[1];
        else eventTargetElementId = "";

        double textMatchingScore = 0;
        if (eventTargetElementText != null && stateNodeTargetElementText != null && !eventTargetElementText.equals("") && !stateNodeTargetElementText.equals("")) {
            textMatchingScore = compareTextWithLemmatization(eventTargetElementText.toLowerCase(), stateNodeTargetElementText.toLowerCase());
        }

        double idMatchingScore = 0;
        if (eventTargetElementId != null && stateNodeTargetElementId != null && !eventTargetElementId.equals("") && !stateNodeTargetElementId.equals("")) {
            idMatchingScore = compareTextWithLemmatization(eventTargetElementId.toLowerCase(), stateNodeTargetElementId.toLowerCase());
        }

        double textIdMatchingScore = 0;
        if (eventTargetElementText != null && stateNodeTargetElementId != null && !eventTargetElementText.equals("") && !stateNodeTargetElementId.equals("")) {
            textIdMatchingScore = compareTextWithLemmatization(eventTargetElementText.toLowerCase(), stateNodeTargetElementId.toLowerCase());
        }

        double idTextMatchingScore = 0;
        if (eventTargetElementId != null && stateNodeTargetElementText != null && !eventTargetElementId.equals("") && !stateNodeTargetElementText.equals("")) {
            idTextMatchingScore = compareTextWithLemmatization(eventTargetElementId.toLowerCase(), stateNodeTargetElementText.toLowerCase());
        }

        double matchingScore = Math.max(textMatchingScore, Math.max(idMatchingScore * 0.9, Math.max(textIdMatchingScore * 0.9, idTextMatchingScore * 0.9)));

        return matchingScore;
    }

    private String findTargetElementText(UiNode stateNodeTargetElement) {
        String stateNodeTargetElementText = stateNodeTargetElement.getAttribute("text");
        if (stateNodeTargetElementText == null || stateNodeTargetElementText.equals(""))
            stateNodeTargetElementText = stateNodeTargetElement.getAttribute("content-desc");
        if (stateNodeTargetElementText == null || stateNodeTargetElementText.equals(""))
            stateNodeTargetElementText = stateNodeTargetElement.getAttribute("hint");
        return stateNodeTargetElementText;
    }

    private String findStateNodeTargetElementId(UiNode stateNodeTargetElement) {
        String stateNodeTargetElementId = "";
        UiNode listViewParentNode = (UiNode)stateNodeTargetElement.getParent();
        while (listViewParentNode != null && (!listViewParentNode.getAttribute("class").equals("android.widget.ListView") &&
                !listViewParentNode.getAttribute("class").equals("android.support.v7.widget.RecyclerView") &&
                !listViewParentNode.getAttribute("class").equals("android.widget.ExpandableListView"))) {
            listViewParentNode = (UiNode) listViewParentNode.getParent();
        }
        if (listViewParentNode == null) {
            stateNodeTargetElementId = stateNodeTargetElement.getAttribute("resource-id");
            if (stateNodeTargetElementId != null && stateNodeTargetElementId.contains("/"))
                stateNodeTargetElementId = stateNodeTargetElementId.split("/")[1];
        }
        return stateNodeTargetElementId;
    }

    private void addStateAndTransitionToFSM(FiniteStateMachine fsm, State currState, State prevState, Event event) {
        if (prevState != null) {
            if(fsm.getState(prevState) == null) fsm.addState(prevState);
            if (fsm.getState(currState) == null) fsm.addState(currState);
            fsm.addTransition(prevState, currState, new Triple<Event, Boolean, Double>(event, true, 0.0));
        } else {
            fsm.addState(currState);
        }
    }

    private List<Triple<JsonNode, JsonRel, JsonNode>> createStaticNodesAndRels() {
        List<Triple<JsonNode, JsonRel, JsonNode>> jsonNodesRels = new ArrayList<Triple<JsonNode, JsonRel, JsonNode>>();
        try {
            String jsonData = sendRestRequest("\"MATCH (n)-[r]->(m) RETURN n,r,m;\"");
            JSONObject jObject = new JSONObject(jsonData);
            JSONArray jArrayResults = jObject.getJSONArray("results");
            for (int i = 0; i < jArrayResults.length(); i++) {
                jObject = jArrayResults.getJSONObject(i);
                JSONArray jArrayData = jObject.getJSONArray("data");
                for (int j = 0; j < jArrayData.length(); j++) {
                    JSONObject dataObject = jArrayData.getJSONObject(j);
                    JSONArray jArrayRow = dataObject.getJSONArray("row");
                    JsonNode from = null, to = null;
                    JsonRel rel = null;
                    for (int k = 0; k < jArrayRow.length(); k++) {
                        JSONObject rowObject = jArrayRow.getJSONObject(k);
                        if (k == 0) {
                            from = new JsonNode(rowObject.getString("id"),
                                    rowObject.getString("ActivityName"),
                                    rowObject.getString("type"));
                        } else if (k == 1) {
                            String id = rowObject.getString("id");
                            rel = new JsonRel(id,
                                    rowObject.getString("action"),
                                    rowObject.getString("text"),
                                    rowObject.getString("class"));
                        } else if (k == 2) {
                            to = new JsonNode(rowObject.getString("id"),
                                    rowObject.getString("ActivityName"),
                                    rowObject.getString("type"));
                        }
                    }
                    if (from != null && to != null && rel != null) jsonNodesRels.add(new Triple<JsonNode, JsonRel, JsonNode>(from, rel, to));
                }
            }
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }

        return jsonNodesRels;
    }

    private void findPreviouslyMigratedEvents() {
        String[] scenarioNameSplitted = scenarioName.split("_");
        if (scenarioNameSplitted.length > 1) {
            int scenarioNamePostfix = Integer.parseInt(scenarioNameSplitted[scenarioNameSplitted.length - 1]);
            if (scenarioNamePostfix > 1) {
                try {
                    StringBuilder previousScenarioName = new StringBuilder();
                    for (int i = 0; i < scenarioNameSplitted.length - 1; i++) {
                        if (i != scenarioNameSplitted.length - 2)   previousScenarioName.append(scenarioNameSplitted[i]).append(" ");
                        else previousScenarioName.append(scenarioNameSplitted[i]).append("_");
                    }
                    scenarioNamePostfix--;
                    previousScenarioName.append(scenarioNamePostfix);
                    InputStream previousScenarioInputStream = InstrumentationRegistry.getTargetContext().getResources().getAssets().open("migrated-events/AppTestMigrator_" + previousScenarioName.toString() + ".java");
                    if (previousScenarioInputStream != null) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(previousScenarioInputStream, "UTF8"));
                        Boolean skip = true;
                        for (String line; (line = br.readLine()) != null;) {
                            if (line.contains("@Test")) skip = false;
                            if (line.contains("@Before")) skip = true;
                            if (!skip) {
                                triggerPreviouslyMigratedEvents(line);
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void triggerPreviouslyMigratedEvents(String line) {
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

    private boolean performReplaceText(State currState, UiNode root, Transition transition, List<Triple<String, State, Event>> targetEvents_temp) {
        boolean matched = false;
        for (int index = 0; index < currState.getActionables().size(); index++) {
            Pair<Event, List<Double>> stateNode = currState.getActionables().get(index);

            UiNode stateNodeTargetElement = stateNode.first.getTargetElement();
            String stateNodeClass = stateNodeTargetElement.getAttribute("class");

            if (stateNodeClass.contains("EditText") && stateNode.second.size() == 0) {
                String resource_id = stateNodeTargetElement.getAttribute("resource-id");
                if (resource_id != null && resource_id.contains("/"))   resource_id = resource_id.split("/")[1];

                String replacement_text_before = stateNodeTargetElement.getAttribute("replacementtext");

                try {
                    pickBestPossibleInput(root, stateNode.first, stateNodeTargetElement, id_inputType.get(resource_id), transition);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                String replacement_text_after = stateNodeTargetElement.getAttribute("replacementtext");
                if(replacement_text_after != null && replacement_text_after.equals(replacement_text_before))    continue;
                if (replacement_text_after != null) {
                    currState.setClickableVisited(stateNode.first, 0);

                    StringBuilder xpath = new StringBuilder();
                    findXPath(xpath, stateNodeTargetElement);

                    String targetEvent = "";
                    int id = 0;
                    if (resource_id != null)    id = InstrumentationRegistry.getTargetContext().getResources().getIdentifier(resource_id, "id", InstrumentationRegistry.getTargetContext().getPackageName());

                    String text = null;
                    if (!stateNodeTargetElement.getAttribute("class").equals("android.widget.ImageButton") && stateNodeTargetElement.getAttribute("text") != null && !stateNodeTargetElement.getAttribute("text").equals("")) {
                        text = stateNodeTargetElement.getAttribute("text");
                    }

                    if (id != 0) {
                        try {
                            onView(withId(id)).perform(replaceText(replacement_text_after), closeSoftKeyboard());
                            targetEvent = "\nonView(withId(R.id." + resource_id + ")).perform(replaceText(\"" + replacement_text_after + "\"), closeSoftKeyboard());";
                        } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e) {
                            try {
                                onView(withXPath(xpath.toString())).perform(replaceText(replacement_text_after), closeSoftKeyboard());
                                targetEvent = "\nonView(withXPath(\"" + xpath.toString() + "\")).perform(replaceText(\"" + replacement_text_after + "\"), closeSoftKeyboard());";
                            } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e1) {
                                if (text != null) {
                                    onView(allOf(withId(id), withText(text))).perform(replaceText(replacement_text_after), closeSoftKeyboard());
                                    targetEvent = "\nonView(allOf(withId(R.id." + resource_id + "), withText(\"" + text + "\")" + ")).perform(replaceText(\"" + replacement_text_after + "\"), closeSoftKeyboard());";
                                }
                            }
                        }
                    } else {
                        try {
                            onView(withXPath(xpath.toString())).perform(replaceText(replacement_text_after), closeSoftKeyboard());
                            targetEvent = "\nonView(withXPath(\"" + xpath.toString() + "\")).perform(replaceText(\"" + replacement_text_after + "\"), closeSoftKeyboard());";
                        } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e) {
                            if (text != null) {
                                onView(withText(text)).perform(click());
                                targetEvent = "\nonView(withText(\"" + text + "\")).perform(click());";
                            }
                        }
                    }

                    if (!targetEvent.equals("")) {
                        targetEvents_temp.add(new Triple<String, State, Event>(targetEvent + "~RANDOM", currState, new Event()));
                        matched = true;
                    }
                }
            }
        }
        return matched;
    }

    private UiNode getRoot() {
        String dumpWindowHierarchy = null;
        try {
            dumpWindowHierarchy = AccessibilityNodeInfoDumper.dumpWindowHierarchy(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()));
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

    private Event pickRandomAction(State currState, Event prevEvent, State prevState) {
        Event nextEvent = null;
        if (prevEvent != null && currState.computeCosineSimilarity(prevState) >= 0.9) {
            int max_longestCommonSubsequence = 0;

            for (Pair<Event, List<Double>> cl : currState.getActionables()) {
                if (cl.first.getTargetElement().getAttribute("class").contains("EditText")) continue;

                List<Double> indexes = cl.second;

                if (indexes.size() == 0) {
                    StringBuilder cl_xpath = new StringBuilder();
                    findXPath(cl_xpath, cl.first.getTargetElement());

                    StringBuilder prevAction_xpath = new StringBuilder();
                    findXPath(prevAction_xpath, prevEvent.getTargetElement());

                    int longestCommonSubsequence = getLongestCommonSubsequence(cl_xpath.toString(), prevAction_xpath.toString());
                    if (longestCommonSubsequence > max_longestCommonSubsequence) {
                        nextEvent = cl.first;

                        max_longestCommonSubsequence = longestCommonSubsequence;
                    }
                }
            }

            if (nextEvent != null && ((!nextEvent.getTargetElement().getAttribute("content-desc").equals("More options")) ||
                    (nextEvent.getTargetElement().getAttribute("content-desc").equals("More options") &&
                            prevEvent.getTargetElement().getAttribute("content-desc").equals("More options"))) &&
                    ((!nextEvent.getTargetElement().getAttribute("content-desc").equals("Open navigation drawer")) ||
                            (nextEvent.getTargetElement().getAttribute("content-desc").equals("Open navigation drawer") &&
                                    prevEvent.getTargetElement().getAttribute("content-desc").equals("Open navigation drawer")))) {
                currState.setClickableVisited(nextEvent, 0);
            }
        }

        if (nextEvent == null) {
            for (Pair<Event, List<Double>> cl : currState.getActionables()) {
                if (cl.first.getTargetElement().getAttribute("class").contains("EditText")) continue;

                List<Double> indexes = cl.second;
                if (indexes.size() == 0) {
                    nextEvent = cl.first;
                    if (nextEvent != null && ((!nextEvent.getTargetElement().getAttribute("content-desc").equals("More options")) ||
                            (nextEvent.getTargetElement().getAttribute("content-desc").equals("More options") && prevEvent != null &&
                                    prevEvent.getTargetElement().getAttribute("content-desc").equals("More options"))) &&
                            ((!nextEvent.getTargetElement().getAttribute("content-desc").equals("Open navigation drawer")) ||
                                    (nextEvent.getTargetElement().getAttribute("content-desc").equals("Open navigation drawer") && prevEvent != null &&
                                            prevEvent.getTargetElement().getAttribute("content-desc").equals("Open navigation drawer")))) {
                        currState.setClickableVisited(nextEvent, 0);
                    }

                    break;
                }
            }
        }
        return nextEvent;
    }

    private void pickBestPossibleInput(UiNode root, Event stateNode, UiNode stateNodeTargetElement, String inputType, Transition transition) throws IOException {
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

        Event event = transition.getLabel().first;
        if (event != null && event.getTargetElement() != null && event.getTargetElement().getAttribute("class") != null && event.getTargetElement().getAttribute("class").contains("EditText")) {
            double SIMILARITY_THRESHOLD = 0.4;

            String ac = event.getType();
            if (ac.equals("VIEW_TEXT_CHANGED")) {
                String similarStateNodeLabel, similarStateNodeText = "";

                if (event.getTargetElement().getAttribute("src") == null || event.getTargetElement().getAttribute("src").equals("")) {
                    similarStateNodeText = event.getTargetElement().getAttribute("text");
                }

                similarStateNodeLabel = event.getTargetElement().getAttribute("label");

                if (event.getTargetElement().getAttribute("resource-id") != null && event.getTargetElement().getAttribute("resource-id").contains("/")) {
                    similarStateNodeLabel += " " + event.getTargetElement().getAttribute("resource-id").split("/")[1];
                }

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

                if ((score > max_Score || (score == max_Score && !transition.getLabel().second)) && score >= SIMILARITY_THRESHOLD) {
                    boolean add = true;
                    if(inputType != null && (((inputType.contains("number") || inputType.contains("phone")) && (!event.getReplacementText().equals("") && !event.getReplacementText().matches("\\d+(?:\\.\\d+)?"))) ||
                            ((!inputType.contains("number") && !inputType.contains("phone")) && event.getReplacementText().matches("\\d+(?:\\.\\d+)?"))))    add = false;
                    if (add) {
                        best_replacement_text = event.getReplacementText();
                    }
                }
            }
        }

        if (best_replacement_text != null) {
            stateNodeTargetElement.addAtrribute("replacementtext", best_replacement_text);
            stateNode.setReplacementText(best_replacement_text);
        }
    }

    private State getCurrentState(FiniteStateMachine fsm) {
        State currState;

        String dumpWindowHierarchy = null;
        try {
            dumpWindowHierarchy = AccessibilityNodeInfoDumper.dumpWindowHierarchy(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()));
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Unable to dump window hierarchy", e.getMessage());
        }

        UiNode root = getRoot();
        updateCheckBoxText(root);

        StateAbstraction abs = new StateAbstraction();
        currState = new State(dumpWindowHierarchy, abs.computeFeatureVector(root), findClickables(root, new ArrayList<Pair<Event, List<Double>>>(), false)/*getActionables(root)*/, getCurrentActivitywithPackage());

        if (fsm.getState(currState) != null) {
            currState = fsm.getState(currState);
        }

        return currState;
    }

    private void dumpUIHierarchy(String filePrefix, String outputDir) {
        String path = Environment.getExternalStorageDirectory().toString() + "/" + outputDir + "/";

        File hierarchyDir = new File(path);
        if (!(hierarchyDir.exists())) hierarchyDir.mkdir();

        //check if filePrefix used before
        filePrefix = getNewFilePrefix(filePrefix, path, 0);
        File hierarchyFile = new File(path + filePrefix + ".xml");

        try {
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).dumpWindowHierarchy(hierarchyFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getNewFilePrefix(String filePrefix, String path, int count) {
        String newFilePrefix = null;

        File[] listOfFiles = new File(path).listFiles();
        if (listOfFiles != null) {
            for (File listOfFile : listOfFiles) {
                if (listOfFile.isFile()) {
                    if (count == 0) {
                        if (listOfFile.getName().equals(filePrefix + ".xml")) {
                            newFilePrefix = filePrefix;
                        }
                    } else {
                        if (listOfFile.getName().equals(filePrefix + " " + count + ".xml")) {
                            newFilePrefix = filePrefix + " " + count;
                        }
                    }
                }
            }
        }

        if (count == 0 && newFilePrefix == null) return filePrefix;
        else if (count > 0 && newFilePrefix == null) return filePrefix + " " + count;
        else return getNewFilePrefix(filePrefix, path, ++count);

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

    private String getCurrentActivitywithPackage() {
        Activity activityInstance = getActivityInstance();
        if (activityInstance != null) {
            return activityInstance.getLocalClassName();
        }
        else return "";
    }

    private void prepareAction(Event nextEvent, Transition transition, String random, List<Triple<String, State, Event>> targetEvents_temp, FiniteStateMachine fsm) throws IOException {
        String targetEvent;
        String type = nextEvent.getType();
        UiNode nextActionTargetElement = nextEvent.getTargetElement();

        StringBuilder xpathStringBuilder = new StringBuilder();
        findXPath(xpathStringBuilder, nextEvent.getTargetElement());
        String xpath = xpathStringBuilder.toString();

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

        if (!random.equals("RANDOM") && transition.getLabel().first != null && transition.getLabel().first.getType() != null) {
            type = transition.getLabel().first.getType();
        }

        UiNode listViewChildNode = nextActionTargetElement;
        UiNode listViewNode = (UiNode) nextActionTargetElement.getParent();
        while (listViewNode != null && (!listViewNode.getAttribute("class").equals("android.widget.ListView") &&
                !listViewNode.getAttribute("class").equals("android.support.v7.widget.RecyclerView"))) {
            listViewChildNode = listViewNode;
            listViewNode = (UiNode) listViewNode.getParent();
        }

        if (navigationDrawerIsOpen) {
            UiNode drawerLayoutChildNode = nextActionTargetElement;
            UiNode drawerLayoutNode = (UiNode) nextActionTargetElement.getParent();
            while (drawerLayoutNode != null && (!drawerLayoutNode.getAttribute("class").equals("android.support.v4.widget.DrawerLayout"))) {
                drawerLayoutChildNode = drawerLayoutNode;
                drawerLayoutNode = (UiNode) drawerLayoutNode.getParent();
            }

            if (drawerLayoutNode != null) {
                int drawerLayoutChildNodeIndex = -1;
                for (int i = 0; i < drawerLayoutNode.getChildrenList().size(); i++) {
                    if (drawerLayoutNode.getChildrenList().get(i).toString().equals(drawerLayoutChildNode.toString()))
                        drawerLayoutChildNodeIndex = i;
                }

                if (drawerLayoutChildNodeIndex == 0) {
                    try {
                        String dumpWindowHierarchyBefore = null;
                        try {
                            dumpWindowHierarchyBefore = AccessibilityNodeInfoDumper.dumpWindowHierarchy(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()));
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.e("Unable to dump window hierarchy", e.getMessage());
                        }
                        onView(withContentDescription("Close navigation drawer")).perform(click());
                        String dumpWindowHierarchyAfter = null;
                        try {
                            dumpWindowHierarchyAfter = AccessibilityNodeInfoDumper.dumpWindowHierarchy(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()));
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.e("Unable to dump window hierarchy", e.getMessage());
                        }
                        if (dumpWindowHierarchyBefore.equals(dumpWindowHierarchyAfter)) {
                            Espresso.pressBack();
                            targetEvent = "\npressBack();";
                        } else {
                            targetEvent = "\nonView(withContentDescription(\"Close navigation drawer\")).perform(click());";
                        }

                        random = "RANDOM";
                        recordEvent(random, targetEvent, nextEvent, targetEvents_temp, fsm);
                        navigationDrawerIsOpen = false;
                    } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException | NoActivityResumedException e) {
                        // do nothing
                    }
                }
            }
        }

        targetEvent = performAction(type, nextActionTargetElement, xpath, id, resource_id, text, listViewChildNode, listViewNode);
        recordEvent(random, targetEvent, nextEvent, targetEvents_temp, fsm);
    }

    private String performAction(String type, UiNode nextActionTargetElement, String xpath, int id, String resource_id, String text, UiNode listViewChildNode, UiNode listViewNode) {
        String targetEvent = null;
        try {
            if (type.contains("VIEW_CLICKED")) {
                String contentDesc = null;
                if (nextActionTargetElement != null)   contentDesc = nextActionTargetElement.getAttribute("content-desc");
                if (contentDesc != null && contentDesc.equals("More options")) {
                    openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getTargetContext());
                    targetEvent = "\nopenActionBarOverflowOrOptionsMenu(getInstrumentation().getTargetContext());";
                    staticNodeType = "OptionsMenu";
                } else {
                    if (nextActionTargetElement != null) {
                        String clazz = nextActionTargetElement.getAttribute("class");
                        if (clazz.contains("Image") || (text != null && text.contains("\n")) || nextActionTargetElement.getAttribute("src") != null)
                            text = null;
                    }

                    if (id != 0) {
                        try {
                            onView(withId(id)).perform(click());
                            targetEvent = "\nonView(withId(R.id." + resource_id + ")).perform(click());";
                        } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e) {
                            try {
                                onView(withXPath(xpath)).perform(click());
                                targetEvent = "\nonView(withXPath(\"" + xpath + "\")).perform(click());";
                            } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e1) {
                                if (text != null) {
                                    try {
                                        onView(allOf(withId(id), withText(text))).perform(click());
                                        targetEvent = "\nonView(allOf(withId(R.id." + resource_id + "), withText(\"" + text + "\")" + ")).perform(click());";
                                    } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e2) {
                                        try {
                                            onView(withXPath(xpath)).perform(click());
                                            targetEvent = "\nonView(withXPath(\"" + xpath + "\")).perform(click());";
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
                                                        targetEvent = "\nonData(anything()).inAdapterView(withXPath(\"" + listView_xpath.toString() + "\")).atPosition(" + positionIndex + ").perform(click());";
                                                    } else {
                                                        onData(anything()).inAdapterView(withId(parentNode_id)).atPosition(positionIndex).perform(click());
                                                        targetEvent = "\nonData(anything()).inAdapterView(withId(\"R.id." + parentNode_resource_id + "\")).atPosition(" + positionIndex + ").perform(click());";
                                                    }
                                                } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e4) {
                                                    try {
                                                        if (parentNode_id == 0) {
                                                            StringBuilder listView_xpath = new StringBuilder();
                                                            findXPath(listView_xpath, listViewNode);
                                                            onView(withXPath(listView_xpath.toString())).perform(actionOnItemAtPosition(positionIndex, click()));
                                                            targetEvent = "\nonView(withXPath(\"" + listView_xpath.toString() + "\")).perform(actionOnItemAtPosition(" + positionIndex + ", click()));";
                                                        } else {
                                                            onView(withId(parentNode_id)).perform(actionOnItemAtPosition(positionIndex, click()));
                                                            targetEvent = "\nonView(withId(R.id." + parentNode_resource_id + ")).perform(actionOnItemAtPosition(" + positionIndex + ", click()));";
                                                        }
                                                    } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e5) {
                                                        e.printStackTrace();
                                                        Log.e("Runtime Exception", e.getMessage());
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    try {
                                        onView(withXPath(xpath)).perform(click());
                                        targetEvent = "\nonView(withXPath(\"" + xpath + "\")).perform(click());";
                                    } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e2) {
                                        e.printStackTrace();
                                        Log.e("Runtime Exception", e.getMessage());
                                    }
                                }
                            }
                        }
                        navigationDrawerIsOpen = false;
                    } else {
                        try {
                            if (contentDesc != null && !contentDesc.equals("")) {
                                if (contentDesc.equals("Close navigation drawer")) {
                                    String dumpWindowHierarchyBefore = null;
                                    try {
                                        dumpWindowHierarchyBefore = AccessibilityNodeInfoDumper.dumpWindowHierarchy(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()));
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                        Log.e("Unable to dump window hierarchy", e.getMessage());
                                    }
                                    onView(withContentDescription(contentDesc)).perform(click());
                                    String dumpWindowHierarchyAfter = null;
                                    try {
                                        dumpWindowHierarchyAfter = AccessibilityNodeInfoDumper.dumpWindowHierarchy(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()));
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                        Log.e("Unable to dump window hierarchy", e.getMessage());
                                    }
                                    if (dumpWindowHierarchyBefore != null && dumpWindowHierarchyAfter != null && dumpWindowHierarchyBefore.equals(dumpWindowHierarchyAfter)) {
                                        Espresso.pressBack();
                                        targetEvent = "\npressBack();";
                                    } else targetEvent = "\nonView(withContentDescription(\"" + contentDesc + "\")).perform(click());";
                                    navigationDrawerIsOpen = false;
                                } else {
                                    onView(withContentDescription(contentDesc)).perform(click());
                                    targetEvent = "\nonView(withContentDescription(\"" + contentDesc + "\")).perform(click());";
                                    if (contentDesc.equals("Open navigation drawer"))   navigationDrawerIsOpen = true;
                                }
                            } else {
                                if (text != null) {
                                    onView(withText(text)).perform(click());
                                    targetEvent = "\nonView(withText(\"" + text + "\")" + ").perform(click());";
                                    navigationDrawerIsOpen = false;
                                } else {
                                    onView(withXPath(xpath)).perform(click());
                                    targetEvent = "\nonView(withXPath(\"" + xpath + "\")).perform(click());";
                                    navigationDrawerIsOpen = false;
                                }
                            }
                        } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e) {
                            try {
                                onView(withXPath(xpath)).perform(click());
                                targetEvent = "\nonView(withXPath(\"" + xpath + "\")).perform(click());";
                                navigationDrawerIsOpen = false;
                            } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e1) {
                                if (text != null) {
                                    onView(withText(text)).perform(click());
                                    targetEvent = "\nonView(withText(\"" + text + "\")).perform(click());";
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
                        targetEvent = "\nonView(withId(R.id." + resource_id + ")).perform(longClick());";
                    } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e) {
                        try {
                            if (text != null) {
                                onView(allOf(withId(id), withText(text))).perform(longClick());
                                targetEvent = "\nonView(allOf(withId(R.id." + resource_id + "), withText(\"" + text + "\")" + ")).perform(longClick());";
                            } else {
                                onView(withXPath(xpath)).perform(longClick());
                                targetEvent = "\nonView(withXPath(\"" + xpath + "\")).perform(longClick());";
                            }
                        } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e1) {
                            onView(withXPath(xpath)).perform(longClick());
                            targetEvent = "\nonView(withXPath(\"" + xpath + "\")).perform(longClick());";
                        }
                    }
                } else {
                    try {
                        onView(withXPath(xpath)).perform(longClick());
                        targetEvent = "\nonView(withXPath(\"" + xpath + "\")).perform(longClick());";
                    } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e) {
                        if (text != null) {
                            onView(withText(text)).perform(longClick());
                            targetEvent = "\nonView(withText(\"" + text + "\")).perform(longClick());";
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

                if (positionIndex != -1) {
                    if (id != 0) {
                        try {
                            onView(withId(id)).perform(click());
                            targetEvent = "\nonView(withId(R.id." + resource_id + ")).perform(click());";
                        } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e) {
                            try {
                                if (text != null) {
                                    onView(allOf(withId(id), withText(text))).perform(click());
                                    targetEvent = "\nonView(allOf(withId(R.id." + resource_id + "), withText(\"" + text + "\")" + ")).perform(click());";
                                }
                            } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e1) {
                                findXPath(new StringBuilder(), nextActionTargetElement);
                                onView(withXPath(xpath)).perform(click());
                                targetEvent = "\nonView(withXPath(\"" + xpath + "\")).perform(click());";
                            }
                        }
                    } else {
                        try {
                            onView(withXPath(xpath)).perform(actionOnItemAtPosition(positionIndex, click()));
                            targetEvent = "\nonView(withXPath(\"" + xpath + "\")).perform(actionOnItemAtPosition(" + positionIndex + ", click()));";
                        } catch (AmbiguousViewMatcherException | NoMatchingViewException | PerformException e) {
                            if (text != null) {
                                onView(withText(text)).perform(actionOnItemAtPosition(positionIndex, click()));
                                targetEvent = "\nonView(withText(\"" + text + "\")).perform(actionOnItemAtPosition(" + positionIndex + ", click()));";
                            }
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
        return targetEvent;
    }

    private void recordEvent(String random, String targetEvent, Event nextEvent, List<Triple<String, State, Event>> targetEvents_temp, FiniteStateMachine fsm) {
        if(targetEvent != null && !targetEvent.equals("")) targetEvents_temp.add(new Triple<String, State, Event>(targetEvent + random, getCurrentState(fsm), nextEvent));
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

    private Event findStaticNextEvent(State state, Triple<JsonNode, JsonRel, JsonNode> static_event) {
        Event stateNode = null;
        String targetId = static_event.first.getId();

        List<String> sourceIds = new ArrayList<String>();
        try {
            String jsonIds = sendRestRequest("\"MATCH (n) WHERE n.ActivityName = '" + getCurrentActivitywithPackage() +
                    "' AND n.type = '" + staticNodeType + "' RETURN n.id;\"");
            try {
                JSONObject jObject = new JSONObject(jsonIds);
                JSONArray jArrayResults = jObject.getJSONArray("results");
                for (int i = 0; i < jArrayResults.length(); i++) {
                    jObject = jArrayResults.getJSONObject(i);
                    JSONArray jArrayData = jObject.getJSONArray("data");
                    for (int j = 0; j < jArrayData.length(); j++) {
                        JSONObject dataObject = jArrayData.getJSONObject(j);
                        JSONArray jArrayRow = dataObject.getJSONArray("row");
                        for (int k = 0; k < jArrayRow.length(); k++) {
                            sourceIds.add(jArrayRow.getString(k));
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            for (String sourceId : sourceIds) {
                String jsonPath = sendRestRequest("\"MATCH p = shortestPath((source)-[*]->(target)) WHERE source.id = '" + sourceId +
                        "' AND target.id = '" + targetId + "' return p;\"");
                try {
                    JSONObject jObject = new JSONObject(jsonPath);
                    JSONArray jArrayResults = jObject.getJSONArray("results");
                    for (int i = 0; i < jArrayResults.length(); i++) {
                        jObject = jArrayResults.getJSONObject(i);
                        JSONArray jArrayData = jObject.getJSONArray("data");
                        JSONObject dataObject = jArrayData.getJSONObject(0);
                        JSONArray jArrayRow = dataObject.getJSONArray("row").getJSONArray(0);
                        if (jArrayRow.length() > 2) {
                            JSONObject rowObject = jArrayRow.getJSONObject(1);
                            String id = rowObject.getString("id");
                            String action = rowObject.getString("action");
                            String text = rowObject.getString("text");
                            String clazz = rowObject.getString("class");

                            if (text.equals("") && id.equals("") && clazz.equals("")) {
                                JSONObject sourceNodeObject = jArrayRow.getJSONObject(0);
                                JSONObject targetNodeObject = jArrayRow.getJSONObject(2);
                                String sourceNodeActivityName = sourceNodeObject.getString("ActivityName");
                                String targetNodeActivityName = targetNodeObject.getString("ActivityName");
                                String sourceNodeType = sourceNodeObject.getString("type");
                                String targetNodeType = targetNodeObject.getString("type");
                                if (sourceNodeActivityName.equals(targetNodeActivityName) && sourceNodeType.equals("ACT")
                                        && targetNodeType.equals("OptionsMenu")) {
                                    for (Pair<Event, List<Double>> node : state.getActionables()) {
                                        UiNode nodeTargetElement = node.first.getTargetElement();
                                        if (nodeTargetElement.getAttribute("content-desc").equals("More options")) {
                                            stateNode = new Event(transformStaticActionToEventType(action), nodeTargetElement, "", "0");
                                        }
                                    }
                                }
                            } else {
                                for (Pair<Event, List<Double>> node : state.getActionables()) {
                                    UiNode nodeTargetElement = node.first.getTargetElement();
                                    String resource_id = null;
                                    if (nodeTargetElement.getAttribute("resource-id") != null && nodeTargetElement.getAttribute("resource-id").contains("/"))
                                        resource_id = nodeTargetElement.getAttribute("resource-id").split("/")[1];

                                    UiNode listViewNode = (UiNode) nodeTargetElement.getParent();
                                    while (listViewNode != null && !listViewNode.getAttribute("class").equals("android.widget.ListView")) {
                                        listViewNode = (UiNode) listViewNode.getParent();
                                    }

                                    String listView_resource_id = null;
                                    if (listViewNode != null && listViewNode.getAttribute("class").equals("android.widget.ListView") &&
                                            listViewNode.getAttribute("resource-id") != null && listViewNode.getAttribute("resource-id").contains("/")) {
                                        listView_resource_id = listViewNode.getAttribute("resource-id").split("/")[1];
                                    }

                                    String nodeTargetElementText = nodeTargetElement.getAttribute("text");
                                    if (nodeTargetElementText == null || nodeTargetElementText.equals(""))  nodeTargetElementText = nodeTargetElement.getAttribute("content-desc");
                                    if (((resource_id != null && resource_id.equals(id) || (listView_resource_id != null && listView_resource_id.equals(id))) &&
                                            (nodeTargetElementText == null || nodeTargetElementText.equals("") || text.equals("") || nodeTargetElementText.equals(text)))) {
                                        stateNode = new Event(transformStaticActionToEventType(action), nodeTargetElement, "", "0");
                                    }

                                }
                            }
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stateNode;
    }

    private String sendRestRequest(String json) throws IOException {
        Request request = new Request.Builder()
                .url("http://10.0.3.2:7474/db/data/transaction/commit")
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "{\"statements\":[{\"statement\":" + json + "}]}"))
                .build();
        Response response = client.newCall(request).execute();
        if (response.body() != null)    return response.body().string();
        else return "";
    }

    private static String filterWord(String str) {
        if (str == null)    return "";

        str = str.toLowerCase();

        List<String> wordsToFilter = new ArrayList<String>() {{
            add("highlight");
            add("bar");
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
            add("action");
            add("picker");
            add("text");
            add("title");
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
                int endIndex = str.length();
                int splitIndex = str.indexOf(" ", startIndex);
                if (str.indexOf("_", startIndex) > -1 && (splitIndex < 0 || str.indexOf("_", startIndex) < splitIndex))
                    splitIndex = str.indexOf("_", startIndex);
                if (splitIndex > -1)    endIndex = splitIndex + 1;
                str = str.replace(str.substring(startIndex, endIndex), "");
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

        return filteredWord.replaceAll("\\s*-\\s*", "").replace("+", "").replace("$", "").replace("...", " ").replace(":", " ").replace("/", " ").replace("(", " ").replace(")", " ").toLowerCase();
    }

    private double compareTextWithLemmatization(String currentStateLeafNodeText, String scenarioStateLeafNodeText) {
        if (!scenarioStateLeafNodeText.trim().equals("") && !currentStateLeafNodeText.trim().equals("") && scenarioStateLeafNodeText.trim().equals(currentStateLeafNodeText.trim())) {
            return 1.01 + (currentStateLeafNodeText.length() - currentStateLeafNodeText.replace(" ", "").length());
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

            int currentStateLeafNodeLemmatizedTextCount = 0;
            int numberOfMatchedTokens = 0;
            for (String currentStateText : currentStateLeafNodeLemmatizedText) {
                currentStateText = filterWord(currentStateText);
                if (currentStateText.equals(""))    continue;
                for (String scenarioStateText : scenarioStateLeafNodeLemmatizedText) {
                    scenarioStateText = filterWord(scenarioStateText);
                    if (scenarioStateText.equals(""))    continue;
                    String key = currentStateText + scenarioStateText;
                    double score = 0;
                    if (currentStateText.equals(scenarioStateText)) {
                        score = 1;
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
        if (response.body() != null)    result = response.body().string();
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

    private List<Pair<Event, List<Double>>> findClickables(UiNode root, List<Pair<Event, List<Double>>> clickables, boolean navigateUp) {
        if (root == null)   return new ArrayList<Pair<Event, List<Double>>>();

        BasicTreeNode[] nodes = root.getChildren();
        for (BasicTreeNode n : nodes) {
            UiNode node = (UiNode) n;

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
                    String leaf_type = leaf.first.getAttribute("class");
                    if (leaf_type.contains("EditText")) testRecorderEvent = new Event("VIEW_TEXT_CHANGED", leaf.first, "", "0");
                    else if (type.equals("android.support.v7.widget.RecyclerView") || type.equals("android.widget.ListView")) {
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
                    }

                    if (testRecorderEvent != null && !alreadyContainsClickable(testRecorderEvent, clickables)) {
                        List<UiNode> webkitNodes = new ArrayList<>();
                        findWebkitAncestors(testRecorderEvent.getTargetElement(), webkitNodes);
                        if (webkitNodes.size() < 1) {
                            List<Double> indexes = new ArrayList<>();
                            clickables.add(new Pair<>(testRecorderEvent, indexes));
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
            }

            if (testRecorderEvent != null && !alreadyContainsClickable(testRecorderEvent, clickables)) {
                List<UiNode> webkitNodes = new ArrayList<>();
                findWebkitAncestors(testRecorderEvent.getTargetElement(), webkitNodes);
                if (webkitNodes.size() < 1) {
                    List<Double> indexes = new ArrayList<>();
                    switch (content_desc) {
                        case "Navigate up":
                            clickables.add(0, new Pair<>(testRecorderEvent, indexes));

                            navigateUp = true;
                            break;
                        case "More options":
                            if (navigateUp)
                                clickables.add(1, new Pair<>(testRecorderEvent, indexes));
                            else
                                clickables.add(0, new Pair<>(testRecorderEvent, indexes));
                            break;
                        default:
                            clickables.add(new Pair<>(testRecorderEvent, indexes));
                            break;
                    }
                }
            }

            findClickables(node, clickables, navigateUp);
        }

        return clickables;
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
        if (resource_id != null && resource_id.contains("/")) {
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
            } else nodes.add(node);
        }

        BasicTreeNode[] nodes = node.getChildren();
        for (BasicTreeNode node1 : nodes) {
            UiNode childNode = (UiNode) node1;
            findUINodesByID(childNode, resourceId, position);
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
            throw new RuntimeException("Please provide the scenario name as an argument.");

        return mode;
    }

    private void getSourceAppScenarios() throws Exception {
        InputStream scenariosInputStream = InstrumentationRegistry.getTargetContext().getResources().getAssets().open("source-scenarios/" + scenarioName);
        scenario = new FiniteStateMachine();

        if (scenariosInputStream != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(scenariosInputStream, "UTF8"))) {
                Event prevEvent = null;
                for (String line; (line = br.readLine()) != null; ) {
                    if (line.startsWith("Transition:")) {
                        br.mark(1000);
                        String nextLine = br.readLine();
                        while (nextLine != null && !nextLine.startsWith("Transition:")) {
                            line += nextLine;
                            nextLine = br.readLine();
                        }
                        br.reset();

                        StateAbstraction abs = new StateAbstraction();

                        UiHierarchyXmlLoader xmlLoader = new UiHierarchyXmlLoader();

                        String fileNameFrom = line.substring(line.indexOf(':') + 1, line.indexOf('>'));
                        InputStream fromInputStream = InstrumentationRegistry.getTargetContext().getResources().getAssets().open("UIAutomator-UIHierarchies/" + fileNameFrom + ".xml");
                        String fromXMLPath = convertStreamToString(fromInputStream);
                        UiNode rootFrom = (UiNode) xmlLoader.parseXml(fromXMLPath);
                        if (rootFrom != null && rootFrom.getChildCount() > 0)
                            rootFrom = (UiNode) rootFrom.getChildren()[rootFrom.getChildCount() - 1];
                        State from = new State(fromXMLPath, abs.computeFeatureVector(rootFrom), findClickables(rootFrom, new ArrayList<Pair<Event, List<Double>>>(), false), fileNameFrom);
                        List<UiNode> fromEditTexts = new ArrayList<>();
                        findEditTextNodes(rootFrom, fromEditTexts);

                        String fileNameTo = line.substring(line.indexOf('>') + 1, line.indexOf('['));
                        State to;
                        UiNode rootTo;
                        List<UiNode> toEditTexts = new ArrayList<>();
                        if (fileNameTo.equals("END")) {
                            to = new State(null, new Hashtable<String, Integer>(), new ArrayList<Pair<Event, List<Double>>>(), "END");
                        } else {
                            InputStream toInputStream = InstrumentationRegistry.getTargetContext().getResources().getAssets().open("UIAutomator-UIHierarchies/" + fileNameTo + ".xml");
                            String toXMLPath = convertStreamToString(toInputStream);
                            rootTo = (UiNode) xmlLoader.parseXml(toXMLPath);
                            if (rootTo != null && rootTo.getChildCount() > 0)
                                rootTo = (UiNode) rootTo.getChildren()[rootTo.getChildCount() - 1];
                            to = new State(toXMLPath, abs.computeFeatureVector(rootTo), findClickables(rootTo, new ArrayList<Pair<Event, List<Double>>>(), false), fileNameTo);
                            findEditTextNodes(rootTo, toEditTexts);
                        }

                        String action = line.substring(line.indexOf('[') + 1, line.indexOf("]["));
                        String targetElement = line.substring(line.indexOf('[', line.indexOf("][") + 1) + 1, line.indexOf(']', line.indexOf("][") + 1));
                        if (!fileNameFrom.contains(targetElement))    fileNameFrom = targetElement;
                        String[] targetElementSplittedBySpace = targetElement.split(" ");
                        String id = targetElementSplittedBySpace[0];
                        if (id.equals("0")) {
                            if (id.equals("0")) id = "";
                        }

                        if (action.contains("load adapter data") || action.contains("Handle transition")
                                || action.contains("scroll to") || action.contains("input method editor")) continue;

                        int position = -1;
                        if (action.contains("position:")) {
                            position = Integer.parseInt(action.substring(action.indexOf("position:") + 10, action.length()).trim());
                        }

                        String type = transformActionToEventType(action);

                        nodes = new ArrayList<>();
                        findUINodesByID(rootFrom, id, position);

                        UiNode node = null;
                        if (nodes.size() > 1) {
                            node = findNode(fileNameFrom, rootFrom);
                        } else if (nodes.size() == 1) {
                            node = nodes.get(0);
                        }

                        if (node == null) {
                            id = fileNameFrom.split(" ")[0];

                            nodes = new ArrayList<>();
                            findUINodesByID(rootFrom, id, position);

                            if (nodes.size() > 1) {
                                node = findNode(fileNameFrom, rootFrom);
                            }
                        }

                        if (nodes.size() == 0) {
                            try {
                                String[] fileNameFromSplitted = fileNameFrom.split(" ");
                                if (fileNameFromSplitted.length == 5) {
                                    fileNameFrom = fileNameFrom + " 1";
                                } else if (fileNameFromSplitted.length == 6) {
                                    int postfix = Integer.parseInt(fileNameFromSplitted[5]);
                                    postfix++;
                                    StringBuilder fileNameFromSB = new StringBuilder();
                                    for (int i = 0; i < fileNameFromSplitted.length - 1; i++) {
                                        if (i != 0) fileNameFromSB.append(" ").append(fileNameFromSplitted[i]);
                                        else fileNameFromSB.append(fileNameFromSplitted[i]);
                                    }
                                    fileNameFrom = fileNameFromSB.toString() + " " + postfix;
                                }
                                fromInputStream = InstrumentationRegistry.getTargetContext().getResources().getAssets().open("UIAutomator-UIHierarchies/" + fileNameFrom + ".xml");
                                fromXMLPath = convertStreamToString(fromInputStream);
                                rootFrom = (UiNode) xmlLoader.parseXml(fromXMLPath);
                                if (rootFrom != null && rootFrom.getChildCount() > 0)
                                    rootFrom = (UiNode) rootFrom.getChildren()[rootFrom.getChildCount() - 1];
                                from = new State(fromXMLPath, abs.computeFeatureVector(rootFrom), findClickables(rootFrom, new ArrayList<Pair<Event, List<Double>>>(), false), fileNameFrom);
                                fromEditTexts = new ArrayList<>();
                                findEditTextNodes(rootFrom, fromEditTexts);

                                findUINodesByID(rootFrom, id, position);
                                if (nodes.size() == 0) {
                                    node = findNode(fileNameFrom, rootFrom);
                                }
                            } catch (IOException ex) {
                                //file does not exist, do nothing
                            }
                        }

                        scenario.addState(from);
                        scenario.addState(to);
                        updateCheckBoxText(rootFrom);

                        if (nodes.size() > 0 && node == null ) node = nodes.get(0);

                        if (node != null && !type.equals("PRESS_BACK")) {
                            String replacementText = determineLabelAndReplacementText(rootFrom, action, type, node, false);
                            Event event = new Event(type, node, replacementText, "0");

                            if (!type.equals("VIEW_CLICKED") || !node.getAttribute("class").contains("EditText")) {
                                scenario.addTransition(from, to, new Triple<>(event, false, 0.0));
                                if ((prevEvent == null || !prevEvent.getType().equals("VIEW_TEXT_CHANGED")) && !type.equals("VIEW_TEXT_CHANGED"))   determineImplicitInput(rootFrom, from, fromEditTexts, to, toEditTexts);
                            }
                            prevEvent = event;
                        } else {
                            Event event = new Event(type, new UiNode(), "", "0");
                            scenario.addTransition(from, to, new Triple<>(event, false, 0.0));
                            prevEvent = event;
                        }
                    }
                }
            }
        }
    }

    private void determineImplicitInput(UiNode rootFrom, State from, List<UiNode> fromEditTexts, State to, List<UiNode> toEditTexts) {
        for (UiNode toEditText: toEditTexts) {
            String toId = toEditText.getAttribute("resource-id");
            String toText = toEditText.getAttribute("text");
            boolean alreadyExist = true;
            for (UiNode fromEditText: fromEditTexts) {
                String fromId = fromEditText.getAttribute("resource-id");
                String fromText = fromEditText.getAttribute("text");
                if (toId != null && fromId != null && toId.equals(fromId) && toText != null &&
                        fromText != null && !toText.equals(fromText))    alreadyExist = false;
            }

            if (!alreadyExist) {
                String editTextReplacementText = determineLabelAndReplacementText(rootFrom, "", "VIEW_TEXT_CHANGED", toEditText, true);
                Event editTextEvent = new Event("VIEW_TEXT_CHANGED", toEditText, editTextReplacementText, "0");
                scenario.addTransition(from, to, new Triple<>(editTextEvent, false, 0.0));
            }
        }
    }

    private String determineLabelAndReplacementText(UiNode rootFrom, String action, String type, UiNode node, Boolean implicitInput) {
        String replacementText = "";
        if (type.equals("VIEW_TEXT_CHANGED")) {
            StringBuilder xpath = new StringBuilder();
            findXPath(xpath, node);

            String label;
            if (node.getAttribute("text") != null && !node.getAttribute("text").equals(""))   label = getEditTextLabel(rootFrom, xpath.toString(), node, true);
            else label = getEditTextLabel(rootFrom, xpath.toString(), node, false);
            node.addAtrribute("label", label);

            if (!implicitInput)   replacementText = action.substring(action.indexOf("(") + 1, action.indexOf(")"));
            else    replacementText = node.getAttribute("text");
        }
        return replacementText;
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

    private String transformActionToEventType(String action) {
        if (action.contains("actionOnItemAtPosition performing ViewAction: single click on item at position:"))
            return "LIST_ITEM_CLICKED";
        else if (action.contains("actionOnItemAtPosition performing ViewAction: long click on item at position:"))
            return "VIEW_LONG_CLICKED";
        else if (action.contains("replace text") || action.contains("type text"))
            return "VIEW_TEXT_CHANGED";
        else if (action.contains("send keyCode: 4")) return "PRESS_BACK";
        else if (action.contains("press editor action")) return "PRESS_EDITOR_ACTION";
        else if (action.equals("long click") || action.contains("swipe"))
            return "VIEW_LONG_CLICKED";
        else return "VIEW_CLICKED";
    }

    private String transformStaticActionToEventType(String action) {
        if (action.contains("item_click"))
            return "LIST_ITEM_CLICKED";
        else if (action.contains("item_long_click"))
            return "VIEW_LONG_CLICKED";
        else if (action.contains("enter_text"))
            return "VIEW_TEXT_CHANGED";
        else if (action.equals("long_click") || action.contains("swipe"))
            return "VIEW_LONG_CLICKED";
        else return "VIEW_CLICKED";
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

    private void findEditTextNodes(UiNode root, List<UiNode> editTextNodes) {
        BasicTreeNode[] nodes = root.getChildren();

        for (BasicTreeNode node1 : nodes) {
            UiNode node = (UiNode) node1;

            String type = node.getAttribute("class");
            if (type.contains("EditText")) editTextNodes.add(node);

            findEditTextNodes(node, editTextNodes);
        }
    }
}