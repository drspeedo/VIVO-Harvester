/*******************************************************************************
 * Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.test.harvester.diff;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.diff.Diff;
import org.vivoweb.harvester.util.InitLog;
import org.vivoweb.harvester.util.repo.JenaConnect;
import org.vivoweb.harvester.util.repo.MemJenaConnect;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.impl.StatementImpl;
import junit.framework.TestCase;

/**
 * @author Chris Haines hainesc@ufl.edu
 */
public class DiffTest extends TestCase {
	/**
	 * SLF4J Logger
	 */
	private static Logger log = LoggerFactory.getLogger(DiffTest.class);
	/** */
	private JenaConnect original;
	/** */
	private JenaConnect incomming;
	/** */
	private JenaConnect output;
	/** */
	private List<Statement> shareStatements;
	/** */
	private List<Statement> subStatements;
	/** */
	private List<Statement> addStatements;
	
	@Override
	protected void setUp() throws Exception {
		InitLog.initLogger(null, null);
		Resource resA = ResourceFactory.createResource("http://test.vivoweb.org/harvester/test/diff/resA");
		Resource resB = ResourceFactory.createResource("http://test.vivoweb.org/harvester/test/diff/resB");
		Resource resC = ResourceFactory.createResource("http://test.vivoweb.org/harvester/test/diff/resC");
		Resource resD = ResourceFactory.createResource("http://test.vivoweb.org/harvester/test/diff/resD");
		Property propA = ResourceFactory.createProperty("http://test.vivoweb.org/harvester/test/diff/propA");
		Property propB = ResourceFactory.createProperty("http://test.vivoweb.org/harvester/test/diff/propB");
		Property propC = ResourceFactory.createProperty("http://test.vivoweb.org/harvester/test/diff/propC");
		Property propD = ResourceFactory.createProperty("http://test.vivoweb.org/harvester/test/diff/propD");
		this.shareStatements = new LinkedList<Statement>();
		this.shareStatements.add(new StatementImpl(resA, propA, ResourceFactory.createTypedLiteral("resApropA")));
		this.shareStatements.add(new StatementImpl(resA, propB, ResourceFactory.createTypedLiteral("resApropB")));
		this.shareStatements.add(new StatementImpl(resA, propC, ResourceFactory.createTypedLiteral("resApropC")));
		this.shareStatements.add(new StatementImpl(resB, propB, ResourceFactory.createTypedLiteral("resBpropB")));
		this.shareStatements.add(new StatementImpl(resB, propD, ResourceFactory.createTypedLiteral("resBpropD")));
		this.shareStatements.add(new StatementImpl(resC, propA, ResourceFactory.createTypedLiteral("resCpropA")));
		this.shareStatements.add(new StatementImpl(resC, propD, ResourceFactory.createTypedLiteral("resCpropD")));
		this.shareStatements.add(new StatementImpl(resD, propB, ResourceFactory.createTypedLiteral("resDpropB")));
		this.shareStatements.add(new StatementImpl(resD, propC, ResourceFactory.createTypedLiteral("resDpropC")));
		this.shareStatements.add(new StatementImpl(resD, propD, ResourceFactory.createTypedLiteral("resDpropD")));
		this.subStatements = new LinkedList<Statement>();
		this.subStatements.add(new StatementImpl(resA, propD, ResourceFactory.createTypedLiteral("resApropDold")));
		this.subStatements.add(new StatementImpl(resB, propA, ResourceFactory.createTypedLiteral("resBpropAold")));
		this.subStatements.add(new StatementImpl(resC, propC, ResourceFactory.createTypedLiteral("resCpropCold")));
		this.addStatements = new LinkedList<Statement>();
		this.addStatements.add(new StatementImpl(resA, propD, ResourceFactory.createTypedLiteral("resApropDnew")));
		this.addStatements.add(new StatementImpl(resB, propA, ResourceFactory.createTypedLiteral("resBpropAnew")));
		this.addStatements.add(new StatementImpl(resC, propB, ResourceFactory.createTypedLiteral("resCpropBnew")));
		this.original = new MemJenaConnect("original");
		this.original.getJenaModel().add(this.shareStatements);
		this.original.getJenaModel().add(this.subStatements);
		this.incomming = new MemJenaConnect("incomming");
		this.incomming.getJenaModel().add(this.shareStatements);
		this.incomming.getJenaModel().add(this.addStatements);
		this.output = new MemJenaConnect("output");
	}
	
	@Override
	protected void tearDown() throws Exception {
		this.original.truncate();
		this.original.close();
		this.incomming.truncate();
		this.incomming.close();
		this.output.truncate();
		this.output.close();
		this.shareStatements.clear();
		this.addStatements.clear();
		this.subStatements.clear();
	}
	
	/**
	 * Test method for {@link org.vivoweb.harvester.diff.Diff#diff(org.vivoweb.harvester.util.repo.JenaConnect, org.vivoweb.harvester.util.repo.JenaConnect, org.vivoweb.harvester.util.repo.JenaConnect, java.lang.String)}.
	 * @throws IOException error
	 */
	@SuppressWarnings("javadoc")
	public final void testDiffAdds() throws IOException {
		log.info("BEGIN testDiffAdds");
		Diff.diff(this.incomming, this.original, this.output, null, null, null, null);
		assertFalse(this.output.isEmpty());
		for(Statement sub : this.subStatements) {
			assertFalse(this.output.getJenaModel().contains(sub));
		}
		for(Statement add : this.addStatements) {
			assertTrue(this.output.getJenaModel().contains(add));
		}
		for(Statement shared : this.shareStatements) {
			assertFalse(this.output.getJenaModel().contains(shared));
		}
		log.info("END testDiffAdds");
	}
	
	/**
	 * Test method for {@link org.vivoweb.harvester.diff.Diff#diff(org.vivoweb.harvester.util.repo.JenaConnect, org.vivoweb.harvester.util.repo.JenaConnect, org.vivoweb.harvester.util.repo.JenaConnect, java.lang.String)}.
	 * @throws IOException error
	 */
	@SuppressWarnings("javadoc")
	public final void testDiffSubs() throws IOException {
		log.info("BEGIN testDiffSubs");
		Diff.diff(this.original, this.incomming, this.output, null, null, null, null);
		assertFalse(this.output.isEmpty());
		for(Statement sub : this.subStatements) {
			assertTrue(this.output.getJenaModel().contains(sub));
		}
		for(Statement add : this.addStatements) {
			assertFalse(this.output.getJenaModel().contains(add));
		}
		for(Statement shared : this.shareStatements) {
			assertFalse(this.output.getJenaModel().contains(shared));
		}
		log.info("END testDiffSubs");
	}
	
	/**
	 * Test method for {@link org.vivoweb.harvester.diff.Diff#diff(org.vivoweb.harvester.util.repo.JenaConnect, org.vivoweb.harvester.util.repo.JenaConnect, org.vivoweb.harvester.util.repo.JenaConnect, java.lang.String)}.
	 * @throws IOException error
	 */
	@SuppressWarnings("javadoc")
	public final void testDiffSame() throws IOException {
		log.info("BEGIN testDiffSame");
		Diff.diff(this.original, this.original, this.output, null, null, null, null);
		assertTrue(this.output.isEmpty());
		for(Statement sub : this.subStatements) {
			assertFalse(this.output.getJenaModel().contains(sub));
		}
		for(Statement add : this.addStatements) {
			assertFalse(this.output.getJenaModel().contains(add));
		}
		for(Statement shared : this.shareStatements) {
			assertFalse(this.output.getJenaModel().contains(shared));
		}
		log.info("END testDiffSame");
	}
	
	/**
	 * Testing Output to XML/RDF
	 * @throws IOException
	 */
	public final void testDiffDumpFile() throws IOException {
		log.info("Begin testDiffDumpFile");
		Diff.diff(this.original, this.incomming, this.output, null, null, null, null);
		//TODO:  Test Ouput File Creation for all three types
		log.info("End testDiffDumpFile");
	}
	
}
