package edu.utexas.cs.utopia.cortado.mockclasses.ir;

/**
 * Dummy function for identifying fragments
 *
 * @author Ben_Sepanski
 */
public class Fragment
{
    /**
     * Begin a user-indicated fragment. This statement is replaced
     * with a nop, and the first statement of the entire
     * statement is taken as the first unit
     *
     * @param ids A comma-delimited list of identifiers of this fragment.
     *             No identifier may be the empty string.
     *             Reserved keywords: may only be used in parFragIDs and commIDs.
     *             - ALL: Indicates all (user-specified)
     *                    fragments (must be only member of ID list)
     *             - NONE: Indicates no fragments (must be only member of ID list)
     *             - SELF: Indicates this fragment
     *             - NOT: Take the complement of the set of fragments
     *                    indicated by the ID list (e.g. "NOT,ID1"
     *                    indicates all (user-specified)
     *                    fragments without id "ID1").
     *                    Must be first member of ID list
     * @param parFragIDs A string of comma-delimited ids identifying
     *                   which fragments this fragment may run in parallel
     *                   with. No IDs may be the empty string.
     *                   If any of fragment B's identifiers
     *                   appear in fragment A's parFragIDs, then fragment B
     *                   may runin parallel with fragment A.
     *                   The reflexive closure of this relation is taken
     *                   (i.e. if B appears in A's parFragIDs list,
     *                    A is included in B's parFragIDs list).
     * @param commIDs A string of comma-delimited ids identifying
     *                which fragments this fragment commutes with.
     *                No IDs may be the empty string.
     *                If any of fragment B's identifiers
     *                appear in fragment A's commIDs, then fragment B
     *                commutes with fragment A.
     *                The reflexive closure of this relation is taken
     *                (i.e. if B appears in A's commIDs list,
     *                 A is included in B's commIDs list)
     */
    public static void beginInitialFragment(String ids, String parFragIDs, String commIDs)
    {
    }

    /**
     * Begin a user-indicated fragment. This statement is replaced
     * with a nop, and the first statement of the entire
     * statement is taken as the first unit
     *
     * @param ids A comma-delimited list of identifiers of this fragment.
     *             No identifier may be the empty string.
     *             Reserved keywords: may only be used in parFragIDs and commIDs.
     *             - ALL: Indicates all (user-specified)
     *                    fragments (must be only member of ID list)
     *             - NONE: Indicates no fragments (must be only member of ID list)
     *             - SELF: Indicates this fragment
     *             - NOT: Take the complement of the set of fragments
     *                    indicated by the ID list (e.g. "NOT,ID1"
     *                    indicates all (user-specified)
     *                    fragments without id "ID1").
     *                    Must be first member of ID list
     * @param parFragIDs A string of comma-delimited ids identifying
     *                   which fragments this fragment may run in parallel
     *                   with. No IDs may be the empty string.
     *                   If any of fragment B's identifiers
     *                   appear in fragment A's parFragIDs, then fragment B
     *                   may run in parallel with fragment A.
     *                   The reflexive closure of this relation is taken
     *                   (i.e. if B appears in A's parFragIDs list,
     *                    A is included in B's parFragIDs list).
     * @param commIDs A string of comma-delimited ids identifying
     *                which fragments this fragment commutes with.
     *                No IDs may be the empty string.
     *                If any of fragment B's identifiers
     *                appear in fragment A's commIDs, then fragment B
     *                commutes with fragment A.
     *                The reflexive closure of this relation is taken
     *                (i.e. if B appears in A's commIDs list,
     *                 A is included in B's commIDs list)
     */
    public static void beginFragment(String ids, String parFragIDs, String commIDs)
    {
    }

    /**
     * End a user-indicated fragment. This statement is replaced
     * with a nop which acts as the lastUnit
     * <p>
     * If the only successor to the nop (in the CFG) is the
     * end of the CCR, and the nop is the only predecessor
     * of the end of the CCR,
     * then the end of the CCR is taken as the
     * end of the fragment instead.
     */
    public static void endFragment()
    {
    }
}
