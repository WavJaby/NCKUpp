package com.wavjaby.lib.restapi;

import com.wavjaby.logger.Logger;

import java.util.*;

import static com.wavjaby.lib.restapi.EndpointHandler.EMPTY_ENDPOINT;

public class PathSegmentNode {
    private static final Logger logger = new Logger("Segment");
    private final String rawSegmentName;
    private final Map<String, PathSegmentNode> child;
    private final EndpointHandler[] endpointHandlers;
    private final List<EndpointHandler> matchAllEndpoints;
    // Is this node ** or *
    private final boolean matchAllSubSegment, matchAllSegment;
    // have match all child
    private PathSegmentNode childMatchAllSubSegment, childMatchAllSegment;

    // Root node
    PathSegmentNode() {
        rawSegmentName = "";
        child = new HashMap<>();
        endpointHandlers = new EndpointHandler[RequestMethod.values().length - 1];
        matchAllEndpoints = null;
        matchAllSubSegment = matchAllSegment = false;
    }

    private PathSegmentNode(String rawSegmentName) {
        this.rawSegmentName = rawSegmentName;
        child = new HashMap<>();
        endpointHandlers = new EndpointHandler[RequestMethod.values().length - 1];
        matchAllEndpoints = null;
        matchAllSubSegment = matchAllSegment = false;
    }

    private PathSegmentNode(String rawSegmentName, boolean isMatchAllSubSegment) {
        this.rawSegmentName = rawSegmentName;
        if (isMatchAllSubSegment) {
            child = null;
            endpointHandlers = new EndpointHandler[RequestMethod.values().length - 1];
            matchAllEndpoints = null;
            matchAllSubSegment = true;
            matchAllSegment = false;
        } else {
            child = new HashMap<>();
            endpointHandlers = null;
            matchAllEndpoints = new ArrayList<>();
            matchAllSubSegment = false;
            matchAllSegment = true;
        }
    }

    PathSegmentNode createPath(PathSegment[] segments) {
        PathSegmentNode node = this;
        boolean isMatchAll = false;
        // Add path to node
        for (PathSegment segment : segments) {
            if (isMatchAll) {
                logger.err("No pattern allow after **");
                return null;
            }

            // Contains **
            if (segment.matchAllAfter) {
                isMatchAll = true;
                if (node.childMatchAllSubSegment == null)
                    node = node.childMatchAllSubSegment = new PathSegmentNode("**", true);
                else
                    node = node.childMatchAllSubSegment;
            }
            // Contains * or {placeholder}
            else if (!segment.fixed) {
                if (node.childMatchAllSegment == null)
                    node = node.childMatchAllSegment = new PathSegmentNode("*", false);
                else
                    node = node.childMatchAllSegment;
            }
            // Fixed name
            else {
                String fixedName = segment.pattern[0];
                node = node.child.computeIfAbsent(fixedName, i -> new PathSegmentNode(fixedName));
            }
        }
        return node;
    }

    public EndpointHandler getEndpoint(String[] segments, RequestMethod method) {
        if (method == RequestMethod.UNKNOWN)
            return EMPTY_ENDPOINT;

        List<PathSegmentNode> matchedNode = new ArrayList<>();
        List<PathSegmentNode> childNode = Collections.singletonList(this);
        // Add path to node
        for (String segment : segments) {
            List<PathSegmentNode> newChildNode = new ArrayList<>();
            for (PathSegmentNode pathSegmentNode : childNode) {
                PathSegmentNode nextNode;
                // Get fixed segment
                if (pathSegmentNode.child != null &&
                        (nextNode = pathSegmentNode.child.get(segment)) != null)
                    newChildNode.add(nextNode);

                if (pathSegmentNode.childMatchAllSegment != null)
                    newChildNode.add(pathSegmentNode.childMatchAllSegment);

                if (pathSegmentNode.childMatchAllSubSegment != null)
                    matchedNode.add(pathSegmentNode.childMatchAllSubSegment);
            }
            childNode = newChildNode;
        }

        // Check fixed
        for (PathSegmentNode node : childNode) {
            if (node.matchAllSubSegment || node.matchAllSegment)
                continue;
            EndpointHandler handler = node.endpointHandlers[method.ordinal()];
            if (handler != null)
                return handler;
        }
        // Check if matchAllSegment node match
        for (PathSegmentNode node : childNode) {
            if (!node.matchAllSegment)
                continue;
            for (EndpointHandler handler : node.matchAllEndpoints) {
                if (handler.checkPathMatch(segments, method))
                    return handler;
            }
        }
        // Get matchAllSubSegment node
        for (int i = matchedNode.size() - 1; i >= 0; i--) {
            EndpointHandler handler = matchedNode.get(i).endpointHandlers[method.ordinal()];
            if (handler != null && handler.checkPathMatch(segments, method))
                return handler;
        }
        return childNode.isEmpty() && matchedNode.isEmpty() ? null : EMPTY_ENDPOINT;
    }

    void addEndpoint(EndpointHandler handler) {
        if (matchAllSegment) {
            int matchWordCount = handler.getMatchWordCount();
            int matchWordLength = handler.getMatchWordLength();
            int i = 0;
            for (EndpointHandler endpoint : matchAllEndpoints) {
                int c = endpoint.getMatchWordCount();
                if (c < matchWordCount || c == matchWordCount && endpoint.getMatchWordLength() < matchWordLength)
                    break;
                i++;
            }
            matchAllEndpoints.add(i, handler);
        } else {
            if (endpointHandlers[handler.getMethodIndex()] != null) {
                logger.err("Conflict path: " + handler.getPath());
            }
            endpointHandlers[handler.getMethodIndex()] = handler;
        }
    }

    public void printStructure() {
        StringBuilder result = new StringBuilder();
        printStructure(0, false, null, result);
        logger.log(result);
    }

    private void printStructure(int index, boolean haveNext, String padding, StringBuilder result) {
        if (padding == null) {
            result.append('\n').append('/').append(this.rawSegmentName);
            padding = "";
        } else {
            result.append('\n').append(padding).append(haveNext ? "├─/" : "└─/").append(this.rawSegmentName).append(' ');
            if (endpointHandlers == null)
                return;
            for (EndpointHandler handler : endpointHandlers) {
                if (handler == null)
                    continue;
                result.append(' ').append(handler.getMethodName());
            }
            padding += haveNext ? "│ " : "  ";
        }

        if (childMatchAllSegment != null)
            childMatchAllSegment.printStructure(index + 1, childMatchAllSubSegment == null, padding, result);
        if (childMatchAllSubSegment != null)
            childMatchAllSubSegment.printStructure(index + 1, child == null || child.isEmpty(), padding, result);

        if (child == null)
            return;
        int last = child.size();
        for (PathSegmentNode subNode : child.values()) {
            subNode.printStructure(index + 1, child.size() > 1 && --last > 0, padding, result);
        }
    }
}
