package com.example.demo.service;

import com.example.demo.model.User;
import com.example.demo.model.UserRelation;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RelationshipResolver {

    private enum Step { UP, DOWN, SPOUSE, SIBLING }

    public static class Graph {
        Map<Long, Set<Long>> parentOf  = new HashMap<>();
        Map<Long, Set<Long>> childOf   = new HashMap<>();
        Map<Long, Long> spouseOf       = new HashMap<>();
        Map<Long, Set<Long>> siblingOf = new HashMap<>();
        Map<Long, String> genderOf     = new HashMap<>();
        Map<Long, User> users          = new HashMap<>();

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

        void addSiblingEdge(User a, User b) {
            siblingOf.computeIfAbsent(a.getId(), k -> new HashSet<>()).add(b.getId());
            siblingOf.computeIfAbsent(b.getId(), k -> new HashSet<>()).add(a.getId());
            users.put(a.getId(), a);
            users.put(b.getId(), b);
        }

        void noteGender(User person, String userGender) {
            if (userGender != null && !"N".equals(userGender)) {
                genderOf.put(person.getId(), userGender);
            }
            users.put(person.getId(), person);
        }
    }

    /** PARENT/CHILD/SPOUSE/SIBLING become real edges — everything else
     *  (GRANDPARENT, UNCLE, COUSIN, INLAW...) is a derived label and is
     *  never used as an edge, so it can never introduce a wrong/ambiguous path. */
    public Graph buildGraph(List<UserRelation> acceptedRelations) {
        Graph g = new Graph();
        for (UserRelation ur : acceptedRelations) {
            User from = ur.getFromUser();
            User to   = ur.getToUser();
            String category = ur.getRelation().getRelationCategory();

            g.noteGender(to, to.getGender());
            g.noteGender(from, from.getGender());

            switch (category) {
                case "PARENT"  -> g.addParentEdge(from, to);
                case "CHILD"   -> g.addParentEdge(to, from);
                case "SPOUSE"  -> g.addSpouseEdge(from, to);
                case "SIBLING" -> g.addSiblingEdge(from, to);
                default -> { }
            }
        }
        return g;
    }

    private record Node(Long id, List<Step> path) {}

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
            for (Long sib : g.siblingOf.getOrDefault(cur.id(), Set.of())) {
                if (sib.equals(toId)) return append(cur.path(), Step.SIBLING);
                if (visited.add(sib)) queue.add(new Node(sib, append(cur.path(), Step.SIBLING)));
            }
        }
        return null;
    }

    private List<Step> append(List<Step> path, Step s) {
        List<Step> np = new ArrayList<>(path);
        np.add(s);
        return np;
    }

    public String resolve(Graph g, Long fromId, Long toId) {
        List<Step> path = findPath(g, fromId, toId, 4);
        if (path == null || path.isEmpty()) return null;

        boolean f = "F".equals(g.genderOf.getOrDefault(toId, "N"));

        String pattern = path.stream()
                .map(s -> switch (s) { case UP -> "U"; case DOWN -> "D"; case SPOUSE -> "S"; case SIBLING -> "B"; })
                .reduce("", String::concat);

        return switch (pattern) {
            case "U"   -> f ? "Mother" : "Father";
            case "D"   -> f ? "Daughter" : "Son";
            case "S"   -> f ? "Wife" : "Husband";
            case "B"   -> f ? "Sister" : "Brother";
            case "UU"  -> f ? "Grandmother" : "Grandfather";
            case "DD"  -> f ? "Granddaughter" : "Grandson";

            case "UD"  -> f ? "Sister" : "Brother";   // shared parent, other child
            case "DU"  -> f ? "Wife" : "Husband";     // shared child, other parent
            case "BU"  -> f ? "Mother" : "Father";     // sibling's parent = my parent
            case "BD"  -> f ? "Niece" : "Nephew";      // sibling's child
            case "BS"  -> f ? "Sister-in-law" : "Brother-in-law";  // sibling's spouse
            case "SB"  -> f ? "Sister-in-law" : "Brother-in-law";  // spouse's sibling

            case "SU"  -> f ? "Mother-in-law" : "Father-in-law";
            case "DS"  -> f ? "Daughter-in-law" : "Son-in-law";

            case "UUD", "UB" -> f ? "Aunt" : "Uncle";
            case "UDD", "BDD" -> f ? "Niece" : "Nephew";
            case "UDS", "SUD", "DUS", "BSU" -> f ? "Sister-in-law" : "Brother-in-law";

            case "UUDD", "UBD" -> "Cousin";

            default -> null;
        };
    }
}