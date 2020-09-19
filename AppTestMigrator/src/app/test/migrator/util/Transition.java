package app.test.migrator.util;

/*
 * 	Representation of Transition
 */

public class Transition{
    private State from;
    private State to;
    private Event label;

    public Transition(State from, State to, Event label){
        this.from = from;
        this.to = to;
        this.label = label;
    }

    public State getFrom(){ return from; }

    public State getTo(){ return to; }

    public Event getLabel(){ return label; }

    public void changeLabel(Event newLabel){ this.label = newLabel; }
}
