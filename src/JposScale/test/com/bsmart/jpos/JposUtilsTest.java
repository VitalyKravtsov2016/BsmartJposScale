package com.bsmart.jpos;

import static org.junit.Assert.*;

import org.junit.Test;

import jpos.JposConst;

public class JposUtilsTest {

    @Test
    public void testGetCapPowerReportingText() {
        assertEquals("JPOS_PR_NONE", JposUtils.getCapPowerReportingText(JposConst.JPOS_PR_NONE));
        assertEquals("JPOS_PR_STANDARD", JposUtils.getCapPowerReportingText(JposConst.JPOS_PR_STANDARD));
        assertEquals("JPOS_PR_ADVANCED", JposUtils.getCapPowerReportingText(JposConst.JPOS_PR_ADVANCED));
        assertEquals("123", JposUtils.getCapPowerReportingText(123));
    }

    @Test
    public void testGetStateText() {
        assertEquals("JPOS_S_CLOSED", JposUtils.getStateText(JposConst.JPOS_S_CLOSED));
        assertEquals("JPOS_S_IDLE", JposUtils.getStateText(JposConst.JPOS_S_IDLE));
        assertEquals("JPOS_S_BUSY", JposUtils.getStateText(JposConst.JPOS_S_BUSY));
        assertEquals("JPOS_S_ERROR", JposUtils.getStateText(JposConst.JPOS_S_ERROR));
        assertEquals("321", JposUtils.getStateText(321));
    }
}

