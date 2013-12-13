/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.admin;

import static org.fest.assertions.Assertions.*;
import static org.testng.Assert.*;

import org.forgerock.opendj.admin.meta.RootCfgDefn;
import org.forgerock.opendj.config.ConfigTestCase;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;


@SuppressWarnings("javadoc")
@Test(singleThreaded = true)
public class TestTopCfgDefnTest extends ConfigTestCase {

    @BeforeClass
    public void setUp() throws Exception {
        disableClassValidationForProperties();
    }

    @Test
    public void testGetInstance() {
        assertNotNull(TopCfgDefn.getInstance());
    }

    @Test
    public void testGetName() {
        assertEquals(TopCfgDefn.getInstance().getName(), "top");
    }

    @Test
    public void testGetAllPropertyDefinitionsIsEmpty() {
        assertTrue(TopCfgDefn.getInstance().getAllPropertyDefinitions().isEmpty());
    }

    @Test
    public void testGetAllRelationDefinitionsIsEmpty() {
        assertTrue(TopCfgDefn.getInstance().getAllRelationDefinitions().isEmpty());
    }

    @Test
    public void testGetAllConstraintsIsEmpty() {
        assertTrue(TopCfgDefn.getInstance().getAllConstraints().isEmpty());
    }

    @Test
    public void testGetAllTagsIsEmpty() {
        assertTrue(TopCfgDefn.getInstance().getAllTags().isEmpty());
    }

    @Test
    public void testGetParentReturnNull() {
        assertNull(TopCfgDefn.getInstance().getParent());
    }

    @Test
    public void testIsTop() {
        assertTrue(TopCfgDefn.getInstance().isTop());
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testGetSynopsis() {
        assertNotNull(TopCfgDefn.getInstance().getSynopsis());
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testGetDescription() {
        assertNotNull(TopCfgDefn.getInstance().getDescription());
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testGetUserFriendlyName() {
        assertNotNull(TopCfgDefn.getInstance().getUserFriendlyName());
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testGetUserFriendlyPluralName() {
        assertNotNull(TopCfgDefn.getInstance().getUserFriendlyPluralName());
    }

    @Test
    public void testGetAllChildren() {
        // load RootCfgDef as child of TopCfgDef, and load all children of RootCfgDef as well
        RootCfgDefn.getInstance();
        assertThat(TopCfgDefn.getInstance().getAllChildren()).isNotEmpty();
    }

}
