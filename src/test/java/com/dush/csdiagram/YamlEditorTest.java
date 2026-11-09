package com.dush.csdiagram;

import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;

import java.util.Arrays;
import java.util.Collections;

public class YamlEditorTest extends ServicesTestCase {

    private static final String SMALL = "!ChronicleServicesCfg {\n"
            + "  queues: {\n"
            + "    a: { path: data/a },\n"
            + "    b: { path: data/b }\n"
            + "  },\n"
            + "  services: {\n"
            + "    s1: { inputs: [ a ], output: b, snapshotQueue: b, implClass: b },\n"
            + "    s2: { inputs: [ b, a ], output: c }\n"
            + "  }\n"
            + "}\n";

    public void testRenameQueueUpdatesEveryReference() {
        open(SMALL);
        assertNull(editor().renameQueue("b", "bb"));
        assertEquals("!ChronicleServicesCfg {\n"
                + "  queues: {\n"
                + "    a: { path: data/a },\n"
                + "    bb: { path: data/b }\n"
                + "  },\n"
                + "  services: {\n"
                + "    s1: { inputs: [ a ], output: bb, snapshotQueue: bb, implClass: b },\n"
                + "    s2: { inputs: [ bb, a ], output: c }\n"
                + "  }\n"
                + "}\n", text());
    }

    public void testRenameUndeclaredQueue() {
        open(SMALL);
        assertNull(editor().renameQueue("c", "cc"));
        assertTrue(text().contains("output: cc }"));
    }

    public void testRenameQueueChecks() {
        open(SMALL);
        assertNotNull(editor().renameQueue("a", "b"));
        assertNotNull(editor().renameQueue("a", "has space"));
        assertNotNull(editor().renameQueue("a", ""));
        assertNotNull(editor().renameQueue("missing", "x"));
        assertEquals(SMALL, text());
    }

    public void testRenameQueueInSample() {
        open(sample());
        assertNull(editor().renameQueue("trades-out", "fills-out"));
        ServicesModel m = model();
        assertFalse(m.queues.containsKey("trades-out"));
        assertTrue(m.queues.get("fills-out").declared);
        assertEquals("fills-out", m.services.get("matchingEngine").output.queue);
        assertEquals("fills-out", m.services.get("positionSvc").inputs.get(0).queue);
        assertEquals("fills-out", m.services.get("auditSvc").inputs.get(1).queue);
        assertEquals("data/trades-out", ServicesModel.find(m.queues.get("fills-out").props, "path").value);
    }

    public void testRenameService() {
        open(SMALL);
        assertNull(editor().renameService("s1", "first"));
        assertTrue(text().contains("    first: { inputs: [ a ]"));
        assertNotNull(editor().renameService("first", "s2"));
    }

    public void testSetScalarKeepsTag() {
        open(sample());
        assertNull(editor().setScalar("b:orders-in", "rollCycle", "HOURLY"));
        assertTrue(text().contains("rollCycle: !RollCycles HOURLY,\n        replicateSets: [ sg1, sg2 ]"));
        assertNull(editor().setScalar("b:orders-in", "master", "sg2"));
        assertEquals("sg2", ServicesModel.find(model().queues.get("orders-in").builder.entries, "master").value);
    }

    public void testSetServiceOutputAndQuoting() {
        open(SMALL);
        assertNull(editor().setScalar("s:s2", "output", "a"));
        assertNull(editor().setScalar("s:s1", "implClass", "a: b"));
        assertTrue(text(), text().contains("s1: { inputs: [ a ], output: b, snapshotQueue: b, implClass: \"a: b\" }"));
        assertTrue(text().contains("s2: { inputs: [ b, a ], output: a }"));
    }

    public void testSetList() {
        open(sample());
        assertNull(editor().setList("b:orders-in", "replicateSets", Collections.singletonList("sg1")));
        assertTrue(text().contains("replicateSets: [ sg1 ],\n        master: sg1"));
        assertNull(editor().setList("s:pnlSvc", "inputs", Arrays.asList("position-out", "md-in", "risk-out")));
        assertEquals(3, model().services.get("pnlSvc").inputs.size());
        assertNull(editor().setList("s:pnlSvc", "inputs", Collections.emptyList()));
        assertTrue(text().contains("inputs: [ ],\n      output: pnl-out"));
    }

    public void testAddPropertyInlineMapping() {
        open(SMALL);
        assertNull(editor().setScalar("q:a", "sourceId", "9"));
        assertTrue(text().contains("    a: { path: data/a, sourceId: 9 },\n"));
    }

    public void testAddPropertyMultilineMapping() {
        open(sample());
        assertNull(editor().setScalar("q:audit-out", "retention", "7d"));
        assertTrue(text(), text().contains("        rollCycle: !RollCycles DAILY\n"
                + "      },\n"
                + "      retention: 7d\n"
                + "    }"));
    }

    public void testAddPropertyBlockMapping() {
        open("queues:\n  in:\n    path: data/in\nservices:\n  svc:\n    output: in\n");
        assertNull(editor().setScalar("q:in", "sourceId", "1"));
        assertEquals("queues:\n  in:\n    path: data/in\n    sourceId: 1\nservices:\n  svc:\n    output: in\n", text());
    }

    public void testAddBuilderThenTaggedEntry() {
        open(sample());
        assertNull(editor().addBuilder("risk-out", "SingleChronicleQueueBuilder"));
        assertTrue(text().contains("risk-out: { path: data/risk-out, sourceId: 3, builder: !SingleChronicleQueueBuilder { } },"));
        assertNotNull("second builder refused", editor().addBuilder("risk-out", "SingleChronicleQueueBuilder"));

        assertNull(editor().setScalar("b:risk-out", "rollCycle", "DAILY", "RollCycles"));
        assertTrue(text(), text().contains("builder: !SingleChronicleQueueBuilder { rollCycle: !RollCycles DAILY } },"));
        assertNull(editor().setList("b:risk-out", "replicateSets", Arrays.asList("sg1", "sg2")));
        assertTrue(text(), text().contains("{ rollCycle: !RollCycles DAILY, replicateSets: [ sg1, sg2 ] } },"));

        ServicesModel.Queue q = model().queues.get("risk-out");
        assertEquals("RollCycles", ServicesModel.find(q.builder.entries, "rollCycle").tag);
    }

    public void testEditUnknownOwner() {
        open(SMALL);
        assertNotNull(editor().setScalar("q:c", "path", "x"));   // c is only referenced
        assertNotNull(editor().setScalar("b:a", "rollCycle", "x"));   // a has no builder
        assertEquals(SMALL, text());
    }

    public void testRenameIsOneUndoableCommand() {
        open(SMALL);
        assertNull(editor().renameQueue("b", "bb"));
        UndoManager undo = UndoManager.getInstance(getProject());
        var textEditor = TextEditorProvider.getInstance().getTextEditor(myFixture.getEditor());
        assertTrue(undo.isUndoAvailable(textEditor));
        undo.undo(textEditor);
        assertEquals(SMALL, text());
    }
}
