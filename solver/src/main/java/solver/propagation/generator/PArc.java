/*
 * Copyright (c) 1999-2012, Ecole des Mines de Nantes
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Ecole des Mines de Nantes nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS AND CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package solver.propagation.generator;

import solver.Solver;
import solver.constraints.Constraint;
import solver.constraints.propagators.Propagator;
import solver.propagation.PropagationEngine;
import solver.propagation.generator.predicate.Predicate;
import solver.recorders.fine.AbstractFineEventRecorder;
import solver.recorders.fine.arc.FineArcEventRecorder;
import solver.variables.Variable;
import solver.variables.view.IView;

import java.util.ArrayList;
import java.util.List;

import static solver.propagation.generator.PrimitiveTools.getVar;
import static solver.propagation.generator.PrimitiveTools.validate;

/**
 * A specific generator for ARC type of event recorder.
 * <br/>
 *
 * @author Charles Prud'homme
 * @since 08/03/12
 */
public class PArc implements Generator<AbstractFineEventRecorder> {

    final List<AbstractFineEventRecorder> eventRecorders;

    static final Predicate[] NOV = new Predicate[0];

    public PArc(PropagationEngine propagationEngine, Variable... variables) {
        this(propagationEngine, variables, NOV);
    }

    public PArc(PropagationEngine propagationEngine, Constraint... constraints) {
        this(propagationEngine, constraints, NOV);
    }

    public PArc(PropagationEngine propagationEngine, Propagator... propagators) {
        this(propagationEngine, propagators, NOV);
    }

    public PArc(PropagationEngine propagationEngine, Variable[] variables, Predicate[] validations) {
        super();
        eventRecorders = new ArrayList<AbstractFineEventRecorder>();
        if (variables.length > 0) {
            Solver solver = variables[0].getSolver();
            propagationEngine.prepareWM(solver);
            for (int i = 0; i < variables.length; i++) {
                Variable var = getVar(variables[i]); // deal with view
                make(var, validations, propagationEngine, solver);
            }
        }
    }

    public PArc(PropagationEngine propagationEngine, Constraint[] constraints, Predicate[] validations) {
        super();
        eventRecorders = new ArrayList<AbstractFineEventRecorder>();
        if (constraints.length > 0) {
            Solver solver = constraints[0].getSolver();
            propagationEngine.prepareWM(solver);
            for (int i = 0; i < constraints.length; i++) {
                Propagator[] propagators = constraints[i].propagators;
                for (int j = 0; j < propagators.length; j++) {
                    Propagator propagator = propagators[j];
                    make(propagator, validations, propagationEngine, solver);
                }
            }
        }
    }

    public PArc(PropagationEngine propagationEngine, Propagator[] propagators, Predicate[] validations) {
        super();
        eventRecorders = new ArrayList<AbstractFineEventRecorder>();
        if (propagators.length > 0) {
            Solver solver = propagators[0].getSolver();
            propagationEngine.prepareWM(solver);
            for (int j = 0; j < propagators.length; j++) {
                Propagator propagator = propagators[j];
                make(propagator, validations, propagationEngine, solver);
            }
        }
    }


    private void make(Variable var, Predicate[] validations, PropagationEngine propagationEngine, Solver solver) {
        int vidx = var.getId();
        Propagator[] propagators = var.getPropagators();
        int[] pindices = var.getPIndices();
        for (int j = 0; j < propagators.length; j++) {
            Propagator prop = propagators[j];
            if (validations.length == 0 || validate(var, validations)) {
                int pidx = prop.getId();
                int pos = pindices[j];
                if (propagationEngine.isMarked(vidx, pidx)) {
                    if (validations.length == 0 || validate(prop, validations)) {
                        propagationEngine.clearWatermark(vidx, pidx);
                        FineArcEventRecorder er = new FineArcEventRecorder(var, prop, pos, solver, propagationEngine);
                        eventRecorders.add(er);
                        propagationEngine.addEventRecorder(er);

                    }
                }
            }
        }
        IView[] views = var.getViews();
        for (int j = 0; j < views.length; j++) {
            make(views[j], validations, propagationEngine, solver);
        }
    }

    private void make(Propagator prop, Predicate[] validations, PropagationEngine propagationEngine, Solver solver) {
        int pidx = prop.getId();
        Variable[] variables = prop.getVars();
        for (int j = 0; j < variables.length; j++) {
            Variable var = variables[j];
            if (validations.length == 0 || validate(var, validations)) {
                int vidx = var.getId();
                if (propagationEngine.isMarked(vidx, pidx)) {
                    if (validations.length == 0 || validate(prop, validations)) {
                        propagationEngine.clearWatermark(vidx, pidx);
                        FineArcEventRecorder er = new FineArcEventRecorder(var, prop, j, solver, propagationEngine);
                        eventRecorders.add(er);
                        propagationEngine.addEventRecorder(er);
                    }
                }
            }
        }
    }

    public static FineArcEventRecorder make(PropagationEngine propagationEngine, Solver solver,
                                            Variable var, Propagator prop, int idx) {
        FineArcEventRecorder er = null;
        int pidx = prop.getId();
        int vidx = var.getId();
        if (propagationEngine.isMarked(vidx, pidx)) {
            propagationEngine.clearWatermark(vidx, pidx);
            er = new FineArcEventRecorder(var, prop, idx, solver, propagationEngine);
            propagationEngine.addEventRecorder(er);
        }
        return er;
    }

    @Override
    public AbstractFineEventRecorder[] getElements() {
        return eventRecorders.toArray(new AbstractFineEventRecorder[eventRecorders.size()]);
    }

    @Override
    public boolean isEmpty() {
        return eventRecorders.isEmpty();
    }
}
