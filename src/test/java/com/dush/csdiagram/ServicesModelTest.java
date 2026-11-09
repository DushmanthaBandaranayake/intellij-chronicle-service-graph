package com.dush.csdiagram;

import com.dush.csdiagram.ServicesModel.Prop;
import com.dush.csdiagram.ServicesModel.Queue;
import com.dush.csdiagram.ServicesModel.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ServicesModelTest extends ServicesTestCase {

    public void testSampleQueuesAndServices() throws Exception {
        open(sample());
        ServicesModel m = model();
        assertEquals(Arrays.asList("orders-in", "md-in", "risk-out", "trades-out", "position-out", "pnl-out", "audit-out"),
                List.copyOf(m.queues.keySet()));
        assertEquals(Arrays.asList("orderGateway", "marketDataFeed", "riskSvc", "matchingEngine", "positionSvc", "pnlSvc", "auditSvc"),
                List.copyOf(m.services.keySet()));
        assertTrue(m.problems.isEmpty());

        Service matching = m.services.get("matchingEngine");
        assertEquals(Arrays.asList("risk-out", "md-in"), matching.inputs.stream().map(r -> r.queue).collect(Collectors.toList()));
        assertEquals("trades-out", matching.output.queue);
        assertEquals("OUTPUT_REVERSE", ServicesModel.find(matching.props, "inputsReplayStrategy").value);
        assertTrue(m.services.get("orderGateway").inputs.isEmpty());
        assertEquals("position-out", m.services.get("positionSvc").snapshot.queue);
        assertNull(ServicesModel.find(m.services.get("positionSvc").props, "snapshotQueue"));

        // Used to preview web/diagram.html outside the IDE
        Files.writeString(Path.of("build/sample.json"), DiagramJson.build(m), StandardCharsets.UTF_8);
    }

    public void testBuilder() {
        open(sample());
        ServicesModel m = model();
        Queue orders = m.queues.get("orders-in");
        assertEquals("SingleChronicleQueueBuilder", orders.builder.type);
        Prop roll = ServicesModel.find(orders.builder.entries, "rollCycle");
        assertEquals("RollCycles", roll.tag);
        assertEquals("FAST_HOURLY", roll.value);
        assertEquals(Arrays.asList("sg1", "sg2"), ServicesModel.find(orders.builder.entries, "replicateSets").items);
        assertEquals("sg1", ServicesModel.find(orders.builder.entries, "master").value);
        assertNull("builder isn't a plain property", ServicesModel.find(orders.props, "builder"));
        assertEquals("data/orders-in", ServicesModel.find(orders.props, "path").value);

        assertNull(m.queues.get("risk-out").builder);
        assertEquals(1, m.queues.get("audit-out").builder.entries.size());
    }

    public void testBlockStyle() {
        open("queues:\n"
                + "  in:\n"
                + "    path: data/in\n"
                + "  out:\n"
                + "    path: data/out\n"
                + "services:\n"
                + "  svc:\n"
                + "    inputs:\n"
                + "      - in\n"
                + "    output: out\n");
        ServicesModel m = model();
        assertEquals(2, m.queues.size());
        assertEquals("in", m.services.get("svc").inputs.get(0).queue);
        assertEquals("out", m.services.get("svc").output.queue);
    }

    public void testUndeclaredAndMissingSections() {
        open("!ChronicleServicesCfg { services: { a: { inputs: [ ghost ], output: out } } }");
        ServicesModel m = model();
        assertFalse(m.queues.get("ghost").declared);
        assertFalse(m.queues.get("out").declared);
        assertTrue(m.problems.contains("No queues section."));
        assertTrue(DiagramJson.build(m).contains("\"declared\":false"));
    }

    public void testSingleInputWithoutBrackets() {
        open("!ChronicleServicesCfg { queues: { a: { } }, services: { s: { inputs: a, output: a } } }");
        assertEquals("a", model().services.get("s").inputs.get(0).queue);
    }

    public void testJsonEscaping() {
        assertEquals("\"a\\\"b\\\\c\\n\\u003c/script>\"", DiagramJson.str("a\"b\\c\n</script>"));
    }

    public void testDetector() {
        assertTrue(ServicesFileDetector.isServicesFile(myFixture.configureByText("service.yaml", "").getVirtualFile()));
        assertTrue(ServicesFileDetector.isServicesFile(myFixture.configureByText("prod.yaml", "!ChronicleServicesCfg { }").getVirtualFile()));
        assertFalse(ServicesFileDetector.isServicesFile(myFixture.configureByText("other.yaml", "a: 1").getVirtualFile()));
    }
}
