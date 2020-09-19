package app.test.migrator.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import app.test.migrator.uiautomator.BasicTreeNode;

/*
 * 	Representation of State
 */

public class State{
	private final double SIMILARITY_THRESHOLD = 0.9999999999999998;
    private List<Transition> incomingTransitions;
    private List<Transition> outgoingTransitions;    
    private String fileName;
    private BasicTreeNode uiHierarchy;
    private Hashtable<String, Integer> features;

    public State(BasicTreeNode uiHierarchy, Hashtable<String, Integer> features, String fileName){
    	incomingTransitions = new ArrayList<Transition>();
    	outgoingTransitions = new ArrayList<Transition>();
        this.fileName = fileName;
        this.uiHierarchy = uiHierarchy;
        this.features = features;
    }

    public String getFileName(){ return fileName; }

    public BasicTreeNode getUIHierarchy(){	return uiHierarchy; }
    
    public Hashtable<String, Integer> getFeatures() { return features; }
    
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
