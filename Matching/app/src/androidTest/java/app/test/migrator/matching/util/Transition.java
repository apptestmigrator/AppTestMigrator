package app.test.migrator.matching.util;

/*
 * 	Representation of Transition
 */

public class Transition{
    private State from;
    private State to;
    private Triple<Event, Boolean, Double> label;

    public Transition(State from, State to, Triple<Event, Boolean, Double> label){
        this.from = from;
        this.to = to;
        this.label = label;
    }

    public State getFrom(){ return from; }

    public State getTo(){ return to; }

    public Triple<Event, Boolean, Double> getLabel(){ return label; }
}