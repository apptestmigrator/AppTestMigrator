package app.test.migrator.matching.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;

import app.test.migrator.matching.util.uiautomator.UiNode;

/*
 * 	Representation of State
 */

public class State{
    private final double SIMILARITY_THRESHOLD = 0.99;
    private List<Transition> incomingTransitions;
    private List<Transition> outgoingTransitions;
    private String fileName;
    private String GUIHierarchy;
    private Hashtable<String, Integer> features;
    private List<Pair<Event, List<Double>>> clickables;
    private List<State> similarStates;
    private Map<Event, Boolean> actions = new LinkedHashMap<Event, Boolean>();
    private boolean visited;
    private int nextActionIndex = 0;
    private int visitedActions = 0;

    public State(String GUIHierarchy, Hashtable<String, Integer> features, List<Pair<Event, List<Double>>> clickables, String fileName){
        incomingTransitions = new ArrayList<Transition>();
        outgoingTransitions = new ArrayList<Transition>();
        this.fileName = fileName;
        this.GUIHierarchy = GUIHierarchy;
        this.features = features;
        this.visited = false;
        this.clickables = clickables;
        this.similarStates = new ArrayList<State>();
    }

    public State () {}

    public String getFileName(){ return fileName; }

    public String getGUIHierarchy(){	return GUIHierarchy; }

    public Hashtable<String, Integer> getFeatures() { return features; }

    public void setVisited(){  this.visited = true;  }

    public boolean getVisited(){  return visited;  }

    public void setSimilarStates(List<State> similarStates){  this.similarStates = similarStates;  }

    public List<State> getSimilarStates(){  return similarStates;  }

    public void setActions(Map<Event, Boolean> actions){  this.actions = actions;  }

    public Map<Event, Boolean> getActions(){  return actions;  }

    public int incrementVisitedActions()    { return visitedActions++; }

    public int getVisitedActionsSize()    { return visitedActions; }

    public int getnextActionIndex(){  return nextActionIndex;  }

    public Event getNextAction(Event prevAction){
        if(actions != null) {
            for(Event action : actions.keySet()){
                if(prevAction != null){
                    UiNode parent = (UiNode) prevAction.getTargetElement().getParent();
                    UiNode node = (UiNode) action.getTargetElement().getParent();
                    if(parent != null && node != null && parent.toString().equals(node.toString()) && !actions.get(action)){
                        actions.put(action, true);
                        return action;
                    }
                }
            }

            for(Event action : actions.keySet()){
                if (!actions.get(action)) {
                    actions.put(action, true);
                    return action;
                }
            }
        }

        return null;
    }

    public List<Pair<Event, List<Double>>> getActionables(){  return clickables;  }

    public void setClickableVisited(Event clickable, double index){
        for(int i = 0; i < clickables.size(); i++){
            if(clickables.get(i).first.toString().equals(clickable.toString())){
                List<Double> visitedIndexes = clickables.get(i).second;
                visitedIndexes.add(index);

                System.out.println("Debug: set visited");
                clickables.set(i, new Pair<Event, List<Double>>(clickables.get(i).first, visitedIndexes));
            }
        }
    }

    public void addOutgoingTransition(Transition transition){
        outgoingTransitions.add(transition);
    }

    public void addIncomingTransition(Transition transition){
        incomingTransitions.add(transition);
    }

    public  List<Transition> getIncomingTransitions(){
        return incomingTransitions;
    }

    public int getIncomingTransitionCount() {
        return incomingTransitions.size();
    }

    public Transition getIncomingTransition(int i) {
        return incomingTransitions.get(i);
    }

    public  List<Transition> getOutgoingTransitions(){
        return outgoingTransitions;
    }

    public  void setOutgoingTransitions(List<Transition> outgoingTransitions){
        this.outgoingTransitions = outgoingTransitions;
    }

    public  void setIncomingTransitions(List<Transition> incomingTransitions){
        this.incomingTransitions = incomingTransitions;
    }

    public int getOutgoingTransitionCount() {
        return outgoingTransitions.size();
    }

    public Transition getOutgoingTransition(int i) {
        return outgoingTransitions.get(i);
    }

    public Transition findTransition(State dest) {
        for (Transition t : outgoingTransitions) {
            if (t.getTo().computeCosineSimilarity(dest) >= SIMILARITY_THRESHOLD){
                return t;
            }
        }
        return null;
    }

    public void removeTransitions() {
        outgoingTransitions = new ArrayList<Transition>();
        incomingTransitions = new ArrayList<Transition>();
    }

    public double computeCosineSimilarity(State s2) {
        double sim = 0;

        if (s2 == null) return sim;

        Set<String> U = new HashSet<String>();
        Hashtable<String, Integer> features2 = s2.getFeatures();

        U.addAll(features.keySet());
        U.addAll(features2.keySet());

        double N1 = 0, N2 = 0, dot = 0;
        for (Iterator<String> iter = U.iterator(); iter.hasNext();) {
            String key = iter.next();

            int v1 = features.containsKey(key) ? features.get(key) : 0;
            int v2 = features2.containsKey(key) ? features2.get(key) : 0;

            dot += v1 * v2;
            N1 += v1 * v1;
            N2 += v2 * v2;
        }

        sim = dot / (Math.sqrt(N1) * Math.sqrt(N2));

        return sim;
    }
}