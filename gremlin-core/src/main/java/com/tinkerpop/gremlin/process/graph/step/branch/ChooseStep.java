package com.tinkerpop.gremlin.process.graph.step.branch;

import com.tinkerpop.gremlin.process.Traversal;
import com.tinkerpop.gremlin.process.Traverser;
import com.tinkerpop.gremlin.process.graph.marker.TraversalHolder;
import com.tinkerpop.gremlin.process.graph.step.util.ComputerAwareStep;
import com.tinkerpop.gremlin.process.traverser.TraverserRequirement;
import com.tinkerpop.gremlin.process.util.TraversalHelper;
import com.tinkerpop.gremlin.util.iterator.IteratorUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A step which offers a choice of two or more Traversals to take.
 *
 * @author Joshua Shinavier (http://fortytwo.net)
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class ChooseStep<S, E, M> extends ComputerAwareStep<S, E> implements TraversalHolder {

    private static final Child[] CHILD_OPERATIONS = new Child[]{Child.SET_HOLDER, Child.MERGE_IN_SIDE_EFFECTS, Child.SET_SIDE_EFFECTS};

    private final Function<S, M> mapFunction;
    private Map<M, Traversal<S, E>> choices;

    public ChooseStep(final Traversal traversal, final Predicate<S> predicate, final Traversal<S, E> trueChoice, final Traversal<S, E> falseChoice) {
        this(traversal,
                (Function) s -> predicate.test((S) s),
                new HashMap() {{
                    put(Boolean.TRUE, trueChoice);
                    put(Boolean.FALSE, falseChoice);
                }});
    }

    public ChooseStep(final Traversal traversal, final Function<S, M> mapFunction, final Map<M, Traversal<S, E>> choices) {
        super(traversal);
        this.mapFunction = mapFunction;
        this.choices = choices;
        for (final Traversal<S, E> child : this.choices.values()) {
            child.asAdmin().addStep(new EndStep(child));
            this.executeTraversalOperations(child, CHILD_OPERATIONS);
        }

    }

    @Override
    public Set<TraverserRequirement> getRequirements() {
        return TraversalHolder.super.getRequirements();
    }

    @Override
    public List<Traversal<S, E>> getGlobalTraversals() {
        return Collections.unmodifiableList(new ArrayList<>(this.choices.values()));
    }

    @Override
    protected Iterator<Traverser<E>> standardAlgorithm() {
        while (true) {
            final Traverser<S> start = this.starts.next();
            final Traversal<S, E> choice = this.choices.get(this.mapFunction.apply(start.get()));
            if (null != choice) {
                choice.asAdmin().addStart(start);
                return choice.asAdmin().getEndStep();
            }
        }
    }

    @Override
    protected Iterator<Traverser<E>> computerAlgorithm() {
        while (true) {
            final Traverser<S> start = this.starts.next();
            final Traversal<S, E> choice = this.choices.get(this.mapFunction.apply(start.get()));
            if (null != choice) {
                start.asAdmin().setStepId(choice.asAdmin().getStartStep().getId());
                return IteratorUtils.of((Traverser) start);
            }
        }
    }

    @Override
    public ChooseStep<S, E, M> clone() throws CloneNotSupportedException {
        final ChooseStep<S, E, M> clone = (ChooseStep<S, E, M>) super.clone();
        clone.choices = new HashMap<>();
        for (final Map.Entry<M, Traversal<S, E>> entry : this.choices.entrySet()) {
            final Traversal<S, E> choiceClone = entry.getValue().clone();
            clone.choices.put(entry.getKey(), choiceClone);
            clone.executeTraversalOperations(choiceClone, CHILD_OPERATIONS);
        }
        return clone;
    }

    @Override
    public String toString() {
        return TraversalHelper.makeStepString(this, this.choices.toString());
    }

    @Override
    public void reset() {
        super.reset();
        this.resetTraversals();
    }
}
