package edu.utexas.cs.utopia.cortado.util.graph;

import com.google.common.collect.ImmutableList;
import com.google.common.graph.Graph;
import com.google.common.graph.Graphs;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("UnstableApiUsage")
public class StronglyConnectedComponents<NodeType>
{
    private final ImmutableList<ImmutableList<NodeType>> components;
    private final ImmutableList<ImmutableList<NodeType>> trueComponents;

    public StronglyConnectedComponents(@Nonnull Graph<NodeType> graph)
    {
        List<ImmutableList<NodeType>> components = new ArrayList<>();

        // Tarjan's algorithm
        Map<NodeType, Integer> nodeToIndex = new HashMap<>();
        Map<NodeType, Integer> nodeToLowLink = new HashMap<>();
        Deque<NodeType> stack = new ArrayDeque<>();
        Set<NodeType> nodesOnStack = new HashSet<>();
        for(NodeType node : graph.nodes())
        {
            if(!nodeToIndex.containsKey(node))
            {
                tarjan(components, graph, node, nodeToIndex, nodeToLowLink, stack, nodesOnStack);
            }
            assert stack.isEmpty();
            assert nodesOnStack.isEmpty();
        }
        assert nodeToIndex.size() == graph.nodes().size();
        assert nodeToLowLink.size() == graph.nodes().size();

        this.components = ImmutableList.copyOf(components);
        final List<ImmutableList<NodeType>> trueComponents = components.stream()
                .filter(comp -> comp.size() > 1
                        || (comp.size() == 1 && graph.successors(comp.get(0)).contains(comp.get(0))))
                .collect(Collectors.toList());
        this.trueComponents = ImmutableList.copyOf(trueComponents);
    }

    @Nonnull
    public ImmutableList<ImmutableList<NodeType>> getComponents()
    {
        return components;
    }

    @Nonnull
    public ImmutableList<ImmutableList<NodeType>> getTrueComponents()
    {
        return trueComponents;
    }

    /**
     * @param graph the graph
     * @return true iff this object is a valid partition of graph into strongly connected components
     */
    public boolean sccIsValid(@Nonnull Graph<NodeType> graph)
    {
        // all components must be non-empty
        if(components.stream().anyMatch(AbstractCollection::isEmpty))
        {
            return false;
        }
        // build map from node to component, ensuring all nodes are in at most one component
        Map<NodeType, Integer> nodeToComponent = new HashMap<>();
        for(int i = 0; i < components.size(); ++i)
        {
            for(NodeType node : components.get(i))
            {
                if(nodeToComponent.containsKey(node))
                {
                    return false;
                }
                nodeToComponent.put(node, i);
            }
        }
        // all nodes must be in at least one component
        if(graph.nodes().stream().anyMatch(node -> !nodeToComponent.containsKey(node)))
        {
            return false;
        }
        // We have now established that all nodes are in exactly one component. Now
        // we need to make sure that nodes are in the same component iff they can both
        // reach each other
        Map<NodeType, Set<NodeType>> nodeToReachableNodes = graph.nodes()
                .stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        node -> Graphs.reachableNodes(graph, node)
                ));
        for(NodeType n1 : graph.nodes())
        {
            final Set<NodeType> reachableFromN1 = nodeToReachableNodes.get(n1);
            for(NodeType n2 : graph.nodes())
            {
                final Set<NodeType> reachableFromN2 = nodeToReachableNodes.get(n2);
                boolean shouldShareSCC = reachableFromN1.contains(n2) && reachableFromN2.contains(n1);
                if(shouldShareSCC && !Objects.equals(nodeToComponent.get(n1), nodeToComponent.get(n2)))
                {
                    return false;
                }
                if(!shouldShareSCC && Objects.equals(nodeToComponent.get(n1), nodeToComponent.get(n2)))
                {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Recursive tarjan's algorithm based on wikipedia implementation
     * https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
     */
    private void tarjan(@Nonnull List<ImmutableList<NodeType>> components,
                        @Nonnull Graph<NodeType> graph,
                        @Nonnull NodeType node,
                        @Nonnull Map<NodeType, Integer> nodeToIndex,
                        @Nonnull Map<NodeType, Integer> nodeToLowLink,
                        @Nonnull Deque<NodeType> stack,
                        @Nonnull Set<NodeType> nodesOnStack)
    {
        assert !nodeToIndex.containsKey(node);
        assert !nodeToLowLink.containsKey(node);
        int index = nodeToIndex.size();
        nodeToIndex.put(node, index);
        nodeToLowLink.put(node, index);
        assert !nodesOnStack.contains(node);
        stack.push(node);
        nodesOnStack.add(node);

        for(NodeType nbr : graph.successors(node))
        {
            if(!nodeToIndex.containsKey(nbr))
            {
                tarjan(components, graph, nbr, nodeToIndex, nodeToLowLink, stack, nodesOnStack);
                assert nodeToLowLink.containsKey(nbr) && nodeToLowLink.containsKey(node);
                int nodeLowLink = nodeToLowLink.get(node);
                int nbrLowLink = nodeToLowLink.get(nbr);
                int newLowLink = Math.min(nodeLowLink, nbrLowLink);
                nodeToLowLink.put(node, newLowLink);
            }
            else if(nodesOnStack.contains(nbr))
            {
                assert nodeToLowLink.containsKey(node);
                int nodeLowLink = nodeToLowLink.get(node);
                int nbrIndex = nodeToIndex.get(nbr);
                int newLowLink = Math.min(nodeLowLink, nbrIndex);
                nodeToLowLink.put(node, newLowLink);
            }
        }

        assert nodeToLowLink.containsKey(node) && nodeToIndex.containsKey(node);
        int nodeLowLink = nodeToLowLink.get(node);
        int nodeIndex = nodeToIndex.get(node);
        if(nodeLowLink == nodeIndex)
        {
            List<NodeType> scc = new ArrayList<>();
            NodeType top;
            do
            {
                top = stack.pop();
                assert nodesOnStack.contains(top);
                nodesOnStack.remove(top);
                scc.add(top);
                assert Graphs.reachableNodes(graph, top).contains(node)
                        && Graphs.reachableNodes(graph, node).contains(top);
            }while(!Objects.equals(top, node));
            components.add(ImmutableList.copyOf(scc));
        }
    }
}
