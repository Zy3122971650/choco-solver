/**
 * This file is part of choco-solver, http://choco-solver.org/
 *
 * Copyright (c) 2018, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.solver.search.loop.learn;

import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.nary.clauses.ClauseStore;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.learn.AbstractEventObserver;
import org.chocosolver.solver.learn.EventRecorder;
import org.chocosolver.solver.learn.ExplanationForSignedClause;
import org.chocosolver.solver.search.strategy.decision.DecisionPath;
import org.chocosolver.solver.search.strategy.decision.IntDecision;

/**
 * A learn implementation that is able to learn signed clause on failure. The implication graph is
 * recorded during propagation and then aanlysed, with the constraint in conflict as input to learn
 * a signed clause to add to the model. <p>
 *
 * The algorithm is based on :
 * <pre>
 *     "A Proof-Producing CSP Solver", M.Vesler and O.Strichman, AAI'10.
 * </pre>
 *
 * <p> Project: choco-solver.
 *
 * @author Charles Prud'homme
 * @since 27/01/2017.
 */
public class LearnSignedClauses<E extends ExplanationForSignedClause> implements Learn {

    /**
     * The model that is watched in order to learn signed clauses from failure
     */
    private final Solver mSolver;
    /**
     * Maintains the number of solutions found, required for {@link #record(Solver)}.
     */
    private long nbsol = 0;
    /**
     * The last explanation computed.
     */
    private E lastExplanation;
    /**
     * The nogood store
     */
    private final ClauseStore ngstore;
    /**
     * Maximum cardinality to add nogoods to the store
     */
    private final int max_card;

    /**
     * Build a learned able to learn signed clauses on conflicts and solutions.
     *
     * @param solver   the solver to exploit
     */
    public LearnSignedClauses(Solver solver) {
        this.mSolver = solver;
        if (solver.getEventObserver() == AbstractEventObserver.SILENT_OBSERVER) {
            solver.setEventObserver(new EventRecorder(solver));
        }
        solver.getModel().getClauseBuilder(); // mandatory to store initial domains
        this.ngstore = mSolver.getModel().getClauseConstraint().getClauseStore();
        this.max_card = mSolver.getModel().getSettings().getMaxLearntClauseCardinality();
    }

    public void setExplanation(E explanation) {
        lastExplanation = explanation;
    }

    public E getExplanation() {
        return (E) lastExplanation;
    }

    @Override
    public void record(Solver solver) {
        if (nbsol == solver.getSolutionCount()) {
            onFailure();
        } else {
            nbsol++;
            onSolution();
        }
    }

    @Override
    public void forget(Solver solver) {
        addLearntConstraint();
        ngstore.forget();
        lastExplanation.recycle();
    }

    private void onFailure() {
        ContradictionException cex = mSolver.getContradictionException();
        assert (cex.v != null) || (cex.c != null) : this.getClass().getName() + ".onContradiction incoherent state";
        lastExplanation.learnSignedClause(cex);

        int upto = mSolver.getDecisionPath().size() - lastExplanation.getAssertingLevel();

        DecisionPath path = mSolver.getDecisionPath();
        // find first refutable decision in decision path
        int i = path.size() - 1;
        IntDecision dec = (IntDecision) path.getDecision(i);
        // skip refuted bottom decisions
        while (i > 1 && !dec.hasNext() && dec.getArity() > 1) { /*i == 0 means ROOT */
            dec = (IntDecision) path.getDecision(--i);
        }
        if(dec.getPosition() - lastExplanation.getAssertingLevel()>0){
            mSolver.getMeasures().incBackjumpCount();
        }
        assert upto >= 0 && upto <= mSolver.getDecisionPath().size();
        mSolver.setJumpTo(upto);
    }

    /**
     * @implNote Optimization is a specific case: a solution is found and the cut has to be
     * propagated until it reaches a failure. Doing so, a better explanation will be provided. It
     * acts like that: <ol> <li> Go up to the root node </li> <li> Apply each decision one by one
     * until a failure is thrown </li> <li> Explain the failure. </li> </ol>
     */
    protected void onSolution() {
        if (!mSolver.getObjectiveManager().isOptimization()) {
            // extract the decision path to build the nogood
            lastExplanation.learnSolution(mSolver.getDecisionPath());
            mSolver.setJumpTo(-1);
        }
    }

    private void addLearntConstraint() {
        if (lastExplanation != null && lastExplanation.getCardinality() > 0
                && lastExplanation.getCardinality() <= max_card) {
            lastExplanation.extractConstraint(mSolver.getModel(), ngstore);
        }
    }
}
