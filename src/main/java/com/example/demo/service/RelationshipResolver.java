package com.example.demo.service;

import com.example.demo.model.User;
import com.example.demo.model.UserRelation;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RelationshipResolver {

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
        void noteGender(User person, String gender) {
            if (gender != null && !"N".equals(gender)) genderOf.put(person.getId(), gender);
            users.put(person.getId(), person);
        }
    }

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

    /** Full sibling group of a person (transitive closure), always includes self. */
    private Set<Long> siblingGroup(Graph g, Long x) {
        Set<Long> group = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        group.add(x);
        stack.push(x);
        while (!stack.isEmpty()) {
            Long cur = stack.pop();
            for (Long sib : g.siblingOf.getOrDefault(cur, Set.of())) {
                if (group.add(sib)) stack.push(sib);
            }
        }
        return group;
    }

    /** Ancestor-generation map for 'start'. At EACH level, sibling
     *  substitution is applied only to that level's own members (fills in
     *  missing parent records via a sibling who does have one) — it is NOT
     *  reapplied to the accumulated result, so a cousin's parent never gets
     *  mistaken for a shared parent. This one distinction is what keeps
     *  Sibling vs Cousin vs Uncle/Nephew from colliding. */
    private Map<Long, Integer> familyDistances(Graph g, Long start, int maxLevel) {
        Map<Long, Integer> dist = new HashMap<>();
        Set<Long> currentLevel = new HashSet<>(Set.of(start));

        for (int level = 1; level <= maxLevel; level++) {
            Set<Long> next = new HashSet<>();
            for (Long person : currentLevel) {
                for (Long sib : siblingGroup(g, person)) {
                    next.addAll(g.parentOf.getOrDefault(sib, Set.of()));
                }
            }
            next.removeIf(dist::containsKey);
            if (next.isEmpty()) break;
            for (Long p : next) dist.put(p, level);
            currentLevel = next;
        }
        return dist;
    }

    private enum Type { PARENT, CHILD, GRANDPARENT, GRANDCHILD, SIBLING, COUSIN, UNCLE_AUNT, NEPHEW_NIECE }

    /** Blood-relation type of 'toId' relative to 'fromId' — gender-independent. */
    private Type bloodType(Graph g, Long fromId, Long toId) {
        if (fromId.equals(toId)) return null;
        if (siblingGroup(g, fromId).contains(toId)) return Type.SIBLING;

        Map<Long, Integer> distFrom = familyDistances(g, fromId, 2);
        if (distFrom.containsKey(toId)) {
            return distFrom.get(toId) == 1 ? Type.PARENT : Type.GRANDPARENT;
        }
        Map<Long, Integer> distTo = familyDistances(g, toId, 2);
        if (distTo.containsKey(fromId)) {
            return distTo.get(fromId) == 1 ? Type.CHILD : Type.GRANDCHILD;
        }

        Integer bestSum = null; int bestA = 0, bestB = 0;
        for (Map.Entry<Long, Integer> e : distFrom.entrySet()) {
            Integer b = distTo.get(e.getKey());
            if (b == null) continue;
            int a = e.getValue();
            int sum = a + b;
            if (bestSum == null || sum < bestSum) { bestSum = sum; bestA = a; bestB = b; }
        }
        if (bestSum == null) return null;

        if (bestA == bestB) return bestA == 1 ? Type.SIBLING : Type.COUSIN;
        if (Math.abs(bestA - bestB) == 1) return bestA < bestB ? Type.NEPHEW_NIECE : Type.UNCLE_AUNT;
        return null;
    }

    private String label(Type type, boolean female) {
        if (type == null) return null;
        return switch (type) {
            case PARENT      -> female ? "Mother" : "Father";
            case CHILD        -> female ? "Daughter" : "Son";
            case GRANDPARENT  -> female ? "Grandmother" : "Grandfather";
            case GRANDCHILD   -> female ? "Granddaughter" : "Grandson";
            case SIBLING      -> female ? "Sister" : "Brother";
            case COUSIN       -> "Cousin";
            case UNCLE_AUNT   -> female ? "Aunt" : "Uncle";
            case NEPHEW_NIECE -> female ? "Niece" : "Nephew";
        };
    }

    private boolean isFemale(Graph g, Long id) {
        return "F".equals(g.genderOf.getOrDefault(id, "N"));
    }

    /** Returns what 'toId' is to 'fromId'. */
    public String resolve(Graph g, Long fromId, Long toId) {
        if (fromId.equals(toId)) return null;

        if (toId.equals(g.spouseOf.get(fromId))) {
            return isFemale(g, toId) ? "Wife" : "Husband";
        }

        Type direct = bloodType(g, fromId, toId);
        if (direct != null) return label(direct, isFemale(g, toId));

        Long sFrom = g.spouseOf.get(fromId);
        if (sFrom != null) {
            Type t = bloodType(g, sFrom, toId);
            if (t == Type.SIBLING) return isFemale(g, toId) ? "Sister-in-law" : "Brother-in-law";
            if (t == Type.PARENT)  return isFemale(g, toId) ? "Mother-in-law" : "Father-in-law";
        }

        Long sTo = g.spouseOf.get(toId);
        if (sTo != null) {
            Type t = bloodType(g, fromId, sTo);
            if (t == Type.SIBLING) return isFemale(g, toId) ? "Sister-in-law" : "Brother-in-law";
            if (t == Type.CHILD)   return isFemale(g, toId) ? "Daughter-in-law" : "Son-in-law";
        }

        return null;
    }
}