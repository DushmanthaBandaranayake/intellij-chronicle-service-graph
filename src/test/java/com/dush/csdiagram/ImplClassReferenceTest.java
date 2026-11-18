package com.dush.csdiagram;

import com.intellij.psi.PsiReference;

/**
 * A service's implClass value is a reference to its class. The test platform has no Java plugin, so the class itself
 * can't be found here; these tests check where the reference is and what it covers.
 */
public class ImplClassReferenceTest extends ServicesTestCase {

    private static final String CONFIG = "!ChronicleServicesCfg {\n"
            + "  queues: { orders-in: { path: data/orders-in, implClass: com.acme.NotAService } },\n"
            + "  services: {\n"
            + "    riskSvc: { inputs: [ orders-in ], output: risk-out, implClass: com.acme.risk.RiskSvcImpl },\n"
            + "    pnlSvc: { inputs: [ risk-out ], implClass: \"com.acme.pnl.PnlSvcImpl\" }\n"
            + "  }\n"
            + "}\n";

    private PsiReference referenceAt(String fileName, String text, String atText) {
        int at = text.indexOf(atText);
        assertTrue(atText, at >= 0);
        myFixture.configureByText(fileName, text.substring(0, at + 3) + "<caret>" + text.substring(at + 3));
        return myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
    }

    public void testServiceImplClassIsAReference() {
        PsiReference ref = referenceAt("services.yaml", CONFIG, "com.acme.risk.RiskSvcImpl");
        assertNotNull(ref);
        assertEquals("com.acme.risk.RiskSvcImpl", ref.getCanonicalText());
        assertTrue("a class outside the project mustn't be an error", ref.isSoft());
    }

    public void testQuotedClassNameLeavesOutTheQuotes() {
        PsiReference ref = referenceAt("services.yaml", CONFIG, "com.acme.pnl.PnlSvcImpl");
        assertNotNull(ref);
        assertEquals("com.acme.pnl.PnlSvcImpl", ref.getRangeInElement().substring(ref.getElement().getText()));
    }

    public void testImplClassOutsideServicesIsNotAReference() {
        assertNull(referenceAt("services.yaml", CONFIG, "com.acme.NotAService"));
    }

    public void testOtherServiceValuesAreNotReferences() {
        assertNull(referenceAt("services.yaml", CONFIG, "risk-out, implClass"));
    }

    public void testOtherYamlFilesAreLeftAlone() {
        String plain = CONFIG.replace("!ChronicleServicesCfg ", "");
        assertNull(referenceAt("application.yaml", plain, "com.acme.risk.RiskSvcImpl"));
    }
}
