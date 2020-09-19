package app.test.migrator.matching.util;

import java.util.ArrayList;
import java.util.List;

public class FiniteStateMachine {
    private List<State> states;
    private List<Transition> transitions;

    public FiniteStateMachine(){
        states = new ArrayList<State>();
        transitions = new ArrayList<Transition>();
    }

    public boolean addState(State v){
        if(getState(v) == null){
            states.add(v);
            return true;
        }

        return false;
    }

    public State getState(State s) {
        for(int i = 0; i < states.size(); i++){
            State state = states.get(i);
            double similarityPercentage = s.computeCosineSimilarity(state);
            double SIMILARITY_THRESHOLD = 0.99;
            if(similarityPercentage >= SIMILARITY_THRESHOLD){
                return state;
            }
        }

        return null;
    }

    public void addTransition(State from, State to, Triple<Event, Boolean, Double> label){
        if(getState(from) == null)	throw new IllegalArgumentException("from is not in the FSM");

        if(!to.getFileName().equals("END") && getState(to) == null)	throw new IllegalArgumentException("to is not in the FSM");

        if(!to.getFileName().equals("END") && from.findTransition(to) != null && from.findTransition(to).getLabel().toString().equals(label))	return;

        Transition transition = new Transition(from, to, label);

        from.addOutgoingTransition(transition);
        to.addIncomingTransition(transition);
        transitions.add(transition);
    }

    public void removeState(State s){
        states.remove(s);
    }

    public List<Transition> getTransitions(){ return transitions; }

    public void setTransitions(List<Transition> transitions){ this.transitions = transitions; }

    public List<State> getStates(){
        return states;
    }

}