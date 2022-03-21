package edu.utexas.cs.utopia.cortado.util.sat.backends;

import com.microsoft.z3.Model;
import edu.utexas.cs.utopia.cortado.util.sat.formula.Interpretation;

/**
 * An interpretation built from a z3 model
 */
public class Z3Interpretation extends Z3PartialInterpretation implements Interpretation
{
    public Z3Interpretation(Model model, Z3Converter z3Converter)
    {
        super(model, z3Converter, true);
    }
}
