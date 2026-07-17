package com.example.demo.service;

import com.example.demo.model.User;
import com.example.demo.model.UserRelation;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RelationshipResolver {

    private enum Step { UP, DOWN, SPOUSE }

    public static class Graph {
        Map<Long, Set<Long>> parentOf = new HashMap<>();
        Map<Long, Set<Long>> childOf  = new HashMap<>();
        Map<Long, Long> spouseOf      = new HashMap<>();
        Map<Long, String> genderOf    = new HashMap<>();
        Map<Long, User> users         = new HashMap<>();

        void addParentEdge(User child, User parent) {
            parentOf.computeIfAbsent(child.getId(), k -> new HashSet<>()).add(parent.getId());
            childOf.computeIfAbsent(parent.getId(), k -> new HashSet<>()).add(child.getId());
            users.put(child.getId(), child);
            users.put(parent.getId(), parent);
        }

        void addSpouseEdge(User a, User b) {
            spouseOf.put(a.getId(), b.getId());
            spouseOf.put(b.getId(), a.getId());
            users.put(a.getId(), a);
            users.put(b.getId(), b);
        }

        void noteGender(User person, String gender) {
            if (gender != null && !"N".equals(gender)) {
                genderOf.put(person.getId(), gender);
            }
            users.put(person.getId(), person);
        }
    }

    /** Builds the primitive graph from ALL accepted relations.
     *  Only PARENT / CHILD / SPOUSE categories become edges — everything else
     *  (SIBLING, UNCLE, GRANDPARENT...) is a derived label and is never used
     *  as an edge, so it can never introduce a wrong or ambiguous path. */
    public Graph buildGraph(List<UserRelation> acceptedRelations) {
        Graph g = new Graph();
        for (UserRelation ur : acceptedRelations) {
            User from = ur.getFromUser();
            User to   = ur.getToUser();
            String category = ur.getRelation().getRelationCategory();
            String gender   = ur.getRelation().getGender();

            // relation describes what 'to' is to 'from' → we learn to's gender
            g.noteGender(to, gender);

            switch (category) {
                case "PARENT" -> g.addParentEdge(from, to);   // to is parent of from
                case "CHILD"  -> g.addParentEdge(to, from);   // to is child of from
                case "SPOUSE" -> g.addSpouseEdge(from, to);
                default -> { /* derived category, ignore as edge on purpose */ }
            }
        }
        return g;
    }

    private record Node(Long id, List<Step> path) {}

    /** Shortest path only — BFS visits each node once, so every person gets exactly
     *  ONE interpretation. This is what eliminates the old ambiguity bug. */
    private List<Step> findPath(Graph g, Long fromId, Long toId, int maxDepth) {
        if (fromId.equals(toId)) return null;

        Queue<Node> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(new Node(fromId, new ArrayList<>()));
        visited.add(fromId);

        while (!queue.isEmpty()) {
            Node cur = queue.poll();
            if (cur.path().size() >= maxDepth) continue;

            for (Long p : g.parentOf.getOrDefault(cur.id(), Set.of())) {
                if (p.equals(toId)) return append(cur.path(), Step.UP);
                if (visited.add(p)) queue.add(new Node(p, append(cur.path(), Step.UP)));
            }
            for (Long c : g.childOf.getOrDefault(cur.id(), Set.of())) {
                if (c.equals(toId)) return append(cur.path(), Step.DOWN);
                if (visited.add(c)) queue.add(new Node(c, append(cur.path(), Step.DOWN)));
            }
            Long sp = g.spouseOf.get(cur.id());
            if (sp != null) {
                if (sp.equals(toId)) return append(cur.path(), Step.SPOUSE);
                if (visited.add(sp)) queue.add(new Node(sp, append(cur.path(), Step.SPOUSE)));
            }
        }
        return null;
    }

    private List<Step> append(List<Step> path, Step s) {
        List<Step> np = new ArrayList<>(path);
        np.add(s);
        return np;
    }

    /** Returns what 'toId' is to 'fromId' — matches DB convention:
     *  UserRelation(from, to, relation) means "to is relation to from". */
    public String resolve(Graph g, Long fromId, Long toId) {
        List<Step> path = findPath(g, fromId, toId, 4);
        if (path == null || path.isEmpty()) return null;

        boolean f = "F".equals(g.genderOf.getOrDefault(toId, "N"));

        String pattern = path.stream()
                .map(s -> switch (s) { case UP -> "U"; case DOWN -> "D"; case SPOUSE -> "S"; })
                .reduce("", String::concat);

        return switch (pattern) {
            case "U"  -> f ? "Mother" : "Father";
            case "D"  -> f ? "Daughter" : "Son";
            case "S"  -> f ? "Wife" : "Husband";
            case "UU" -> f ? "Grandmother" : "Grandfather";
            case "DD" -> f ? "Granddaughter" : "Grandson";

            // UD = up to shared parent, down to other child      → Sibling
            // DU = down to shared child, up to child's other parent → Spouse
            case "UD" -> f ? "Sister" : "Brother";
            case "DU" -> f ? "Wife" : "Husband";

            case "SU" -> f ? "Mother-in-law" : "Father-in-law";   // spouse's parent
            case "DS" -> f ? "Daughter-in-law" : "Son-in-law";    // child's spouse

            case "UUD" -> f ? "Aunt" : "Uncle";                   // grandparent's other child
            case "UDD" -> f ? "Niece" : "Nephew";                 // sibling's child
            case "UDS", "SUD", "DUS" -> f ? "Sister-in-law" : "Brother-in-law";

            case "UUDD" -> "Cousin";                              // grandparent's grandchild via sibling branch

            default -> null; // unsupported/ambiguous path → skip rather than guess wrong
        };
    }
}