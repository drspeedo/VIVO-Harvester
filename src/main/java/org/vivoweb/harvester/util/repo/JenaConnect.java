/*******************************************************************************
 * Copyright (c) 2010 Christopher Haines, Dale Scheppler, Nicholas Skaggs, Stephen V. Williams. All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which
 * accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html Contributors:
 * Christopher Haines, Dale Scheppler, Nicholas Skaggs, Stephen V. Williams - initial API and implementation
 ******************************************************************************/
package org.vivoweb.harvester.util.repo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.FileSystemException;
import org.apache.commons.vfs.VFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.util.InitLog;
import org.vivoweb.harvester.util.args.ArgDef;
import org.vivoweb.harvester.util.args.ArgList;
import org.vivoweb.harvester.util.args.ArgParser;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import com.hp.hpl.jena.graph.GraphEvents;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFormatter;
import com.hp.hpl.jena.query.Syntax;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFWriter;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.sparql.resultset.ResultSetFormat;
import com.hp.hpl.jena.update.UpdateAction;
import com.hp.hpl.jena.update.UpdateFactory;

/**
 * Connection Helper for Jena Models
 * @author Christopher Haines (hainesc@ctrip.ufl.edu)
 */
public abstract class JenaConnect {
	/**
	 * SLF4J Logger
	 */
	private static Logger log = LoggerFactory.getLogger(JenaConnect.class);
	/**
	 * Model we are connecting to
	 */
	private Model jenaModel;
	/**
	 * The modelname
	 */
	private String modelName;
	/**
	 * JDBC Connection Line
	 */
	private final String dbUrl;
	/**
	 * JDBC username
	 */
	private final String dbUser;
	/**
	 * JDBC password
	 */
	private final String dbPass;
	/**
	 * Database Type (for Jena)
	 */
	private final String dbType;
	/**
	 * JDBC driver class
	 */
	private final String dbClass;
	
	/**
	 * Base Constructor
	 * @param dbUrl the JDBC Connection Line
	 * @param dbUser the JDBC username
	 * @param dbPass the JDBC password
	 * @param dbType the Database Type (for Jena)
	 * @param dbClass the JDBC driver class
	 */
	protected JenaConnect(String dbUrl, String dbUser, String dbPass, String dbType, String dbClass) {
		this.dbUrl = dbUrl;
		this.dbUser = dbUser;
		this.dbPass = dbPass;
		this.dbType = dbType;
		this.dbClass = dbClass;
	}
	
	/**
	 * Clone Constructor
	 * @param original the original to clone
	 */
	protected JenaConnect(JenaConnect original) {
		this.dbUrl = original.dbUrl;
		this.dbUser = original.dbUser;
		this.dbPass = original.dbPass;
		this.dbType = original.dbType;
		this.dbClass = original.dbClass;
	}
	
	/**
	 * Get the dbType for Jena
	 * @return the dbType
	 */
	protected String getDbType() {
		return this.dbType;
	}
	
	/**
	 * Build a new jdbc connection using connect info
	 * @return a new jdbc connection
	 * @throws IOException error making jdbc connection
	 */
	protected Connection buildConnection() throws IOException {
		try {
			Class.forName(this.dbClass);
			return DriverManager.getConnection(this.dbUrl, this.dbUser, this.dbPass);
		} catch(SQLException e) {
			throw new IOException(e.getMessage(),e);
		} catch(ClassNotFoundException e) {
			throw new IOException(e.getMessage(),e);
		}
	}
	
	/**
	 * Factory (connects to the same jena triple store as another jena connect, but uses a different named model)
	 * @param newModelName the model name to use
	 * @return the new jenaconnect
	 * @throws IOException unable to secure db connection
	 */
	public abstract JenaConnect neighborConnectClone(String newModelName) throws IOException;
	
	/**
	 * Config Stream Based Factory that overrides parameters
	 * @param configStream the config input stream
	 * @param overrideParams the parameters to override the file with
	 * @return JenaConnect instance
	 * @throws IOException error connecting
	 */
	public static JenaConnect parseConfig(InputStream configStream, Map<String,String> overrideParams) throws IOException {
		Map<String,String> paramList = new JenaConnectConfigParser().parseConfig(configStream);
		if(overrideParams != null) {
			for(String key : overrideParams.keySet()) {
				paramList.put(key, overrideParams.get(key));
			}
		}
		for(String param : paramList.keySet()) {
			if(!param.equalsIgnoreCase("dbUser") && !param.equalsIgnoreCase("dbPass")) {
				JenaConnect.log.debug("'" + param + "' - '" + paramList.get(param) + "'");
			}
		}
		return build(paramList);
	}
	
	/**
	 * Config File Based Factory
	 * @param configFile the vfs config file descriptor
	 * @return JenaConnect instance
	 * @throws IOException error connecting
	 */
	public static JenaConnect parseConfig(FileObject configFile) throws IOException {
		return parseConfig(configFile, null);
	}
	
	/**
	 * Config File Based Factory that overrides parameters
	 * @param configFile the vfs config file descriptor
	 * @param overrideParams the parameters to override the file with
	 * @return JenaConnect instance
	 * @throws IOException error connecting
	 */
	public static JenaConnect parseConfig(FileObject configFile, Map<String,String> overrideParams) throws IOException {
		InputStream confStream = (configFile == null)?null:configFile.getContent().getInputStream();
		return parseConfig(confStream, overrideParams);
	}
	
	/**
	 * Config File Based Factory
	 * @param configFile the config file descriptor
	 * @return JenaConnect instance
	 * @throws IOException error connecting
	 */
	public static JenaConnect parseConfig(File configFile) throws IOException {
		return parseConfig(configFile, null);
	}
	
	/**
	 * Config File Based Factory
	 * @param configFile the config file descriptor
	 * @param overrideParams the parameters to override the file with
	 * @return JenaConnect instance
	 * @throws IOException error connecting
	 */
	public static JenaConnect parseConfig(File configFile, Map<String,String> overrideParams) throws IOException {
		InputStream confStream = (configFile == null)?null:VFS.getManager().resolveFile(new File("."), configFile.getAbsolutePath()).getContent().getInputStream();
		return parseConfig(confStream, overrideParams);
	}
	
	/**
	 * Config File Based Factory
	 * @param configFileName the config file path
	 * @return JenaConnect instance
	 * @throws IOException xml parse error
	 */
	public static JenaConnect parseConfig(String configFileName) throws IOException {
		return parseConfig(configFileName, null);
	}
	
	/**
	 * Config File Based Factory
	 * @param configFileName the config file path
	 * @param overrideParams the parameters to override the file with
	 * @return JenaConnect instance
	 * @throws IOException xml parse error
	 */
	public static JenaConnect parseConfig(String configFileName, Map<String,String> overrideParams) throws IOException {
		InputStream confStream = (configFileName == null)?null:VFS.getManager().resolveFile(new File("."), configFileName).getContent().getInputStream();
		return parseConfig(confStream, overrideParams);
	}
	
	/**
	 * Build a JenaConnect based on the given parameter set
	 * @param params the value map
	 * @return the JenaConnect
	 * @throws IOException error connecting to jena model
	 */
	private static JenaConnect build(Map<String,String> params) throws IOException {
		// for(String param : params.keySet()) {
		// log.debug(param+" => "+params.get(param));
		// }
		if(!params.containsKey("type")) {
			throw new IllegalArgumentException("Must specify 'type' parameter {'rdb','sdb','mem'}");
		}
		JenaConnect jc;
		if(params.get("type").equalsIgnoreCase("memory")) {
			jc = new MemJenaConnect(params.get("modelName"));
		} else if(params.get("type").equalsIgnoreCase("rdb")) {
			jc = new RDBJenaConnect(params.get("dbUrl"), params.get("dbUser"), params.get("dbPass"), params.get("dbType"), params.get("dbClass"), params.get("modelName"));
		} else if(params.get("type").equalsIgnoreCase("sdb")) {
			jc = new SDBJenaConnect(params.get("dbUrl"), params.get("dbUser"), params.get("dbPass"), params.get("dbType"), params.get("dbClass"), params.get("dbLayout"), params.get("modelName"));
		} else {
			throw new IllegalArgumentException("unknown type: " + params.get("type"));
		}
		return jc;
	}
	
	/**
	 * Get the size of a jena model
	 * @return the number of statement in this model
	 */
	public int size() {
		//Display count
		int jenaCount = 0;
		for(StmtIterator jenaStmtItr = getJenaModel().listStatements(); jenaStmtItr.hasNext(); jenaStmtItr.next()) {
			jenaCount++;
		}
		return jenaCount;
	}
	
	/**
	 * Get the dataset for this connection
	 * Can be very expensive when using RDB connections (SDB and Mem are fine)
	 * @return the database connection's dataset
	 * @throws IOException error connecting
	 */
	public abstract Dataset getConnectionDataSet() throws IOException;
	
	/**
	 * Load in RDF
	 * @param in input stream to read rdf from
	 * @param namespace the base uri to use for imported uris
	 * @param language the language the rdf is in. Predefined values for lang are "RDF/XML", "N-TRIPLE", "TURTLE" (or
	 * "TTL") and "N3". null represents the default language, "RDF/XML". "RDF/XML-ABBREV" is a synonym for "RDF/XML"
	 */
	public void loadRdfFromStream(InputStream in, String namespace, String language) {
		getJenaModel().read(in, namespace, language);
		log.debug("RDF Data was loaded");
	}
	
	/**
	 * Load the RDF from a file
	 * @param fileName the file to read from
	 * @param namespace the base uri to use for imported uris
	 * @param language the language the rdf is in. Predefined values for lang are "RDF/XML", "N-TRIPLE", "TURTLE" (or
	 * "TTL") and "N3". null represents the default language, "RDF/XML". "RDF/XML-ABBREV" is a synonym for "RDF/XML"
	 * @throws FileSystemException error accessing file
	 */
	public void loadRdfFromFile(String fileName, String namespace, String language) throws FileSystemException {
		this.loadRdfFromStream(VFS.getManager().resolveFile(new File("."), fileName).getContent().getInputStream(), namespace, language);
	}
	
	/**
	 * Load in RDF
	 * @param rdf rdf string
	 * @param namespace the base uri to use for imported uris
	 * @param language the language the rdf is in. Predefined values for lang are "RDF/XML", "N-TRIPLE", "TURTLE" (or
	 * "TTL") and "N3". null represents the default language, "RDF/XML". "RDF/XML-ABBREV" is a synonym for "RDF/XML"
	 */
	public void loadRdfFromString(String rdf, String namespace, String language) {
		this.loadRdfFromStream(new ByteArrayInputStream(rdf.getBytes()), namespace, language);
	}
	
	/**
	 * Load in RDF from a model
	 * @param jc the model to load in
	 */
	public void loadRdfFromJC(JenaConnect jc) {
		getJenaModel().add(jc.getJenaModel());
		log.debug("RDF Data was loaded");
	}
	
	/**
	 * Export all RDF
	 * @param out output stream to write rdf to
	 * @throws IOException error writing to stream
	 */
	public void exportRdfToStream(OutputStream out) throws IOException {
		RDFWriter fasterWriter = this.jenaModel.getWriter("RDF/XML");
		fasterWriter.setProperty("showXmlDeclaration", "true");
		fasterWriter.setProperty("allowBadURIs", "true");
		fasterWriter.setProperty("relativeURIs", "");
		OutputStreamWriter osw = new OutputStreamWriter(out, Charset.availableCharsets().get("UTF-8"));
		fasterWriter.write(this.jenaModel, osw, "");
		osw.flush();
		out.flush();
		log.debug("RDF/XML Data was exported");
	}
	
	/**
	 * Export all RDF
	 * @return the rdf
	 * @throws IOException error writing to string
	 */
	public String exportRdfToString() throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		exportRdfToStream(baos);
		return baos.toString();
	}
	
	/**
	 * Export the RDF to a file
	 * @param fileName the file to write to
	 * @throws IOException error writing to file
	 */
	public void exportRdfToFile(String fileName) throws IOException {
		this.exportRdfToStream(VFS.getManager().resolveFile(new File("."), fileName).getContent().getOutputStream(false));
	}
	
	/**
	 * Remove RDF from another JenaConnect
	 * @param inputJC the Model to read from
	 */
	public void removeRdfFromJC(JenaConnect inputJC) {
		this.jenaModel.remove(inputJC.getJenaModel());
	}
	
	/**
	 * Remove RDF from an input stream
	 * @param in input stream to read rdf from
	 * @param namespace the base uri to use for imported uris
	 * @param language the language the rdf is in. Predefined values for lang are "RDF/XML", "N-TRIPLE", "TURTLE" (or
	 * "TTL") and "N3". null represents the default language, "RDF/XML". "RDF/XML-ABBREV" is a synonym for "RDF/XML"
	 * @throws IOException error connecting
	 */
	public void removeRdfFromStream(InputStream in, String namespace, String language) throws IOException {
		this.removeRdfFromJC(new MemJenaConnect(in, namespace, language));
		log.debug("RDF Data was removed");
	}
	
	/**
	 * Remove the RDF from a file
	 * @param fileName the file to read from
	 * @param namespace the base uri to use for imported uris
	 * @param language the language the rdf is in. Predefined values for lang are "RDF/XML", "N-TRIPLE", "TURTLE" (or
	 * "TTL") and "N3". null represents the default language, "RDF/XML". "RDF/XML-ABBREV" is a synonym for "RDF/XML"
	 * @throws IOException error connecting
	 */
	public void removeRdfFromFile(String fileName, String namespace, String language) throws IOException {
		this.removeRdfFromStream(VFS.getManager().resolveFile(new File("."), fileName).getContent().getInputStream(), namespace, language);
	}
	
	/**
	 * Add RDF from another JenaConnect
	 * @param inputJC the Model to read from
	 */
	public void importRdfFromJC(JenaConnect inputJC) {
		this.jenaModel.add(inputJC.getJenaModel());
	}
	
	/**
	 * Removes all records in a RecordHandler from the model
	 * @param rh the RecordHandler to pull records from
	 * @param namespace the base uri to use for imported uris
	 * @return number of records removed
	 * @throws IOException error connecting
	 */
	public int removeRdfFromRH(RecordHandler rh, String namespace) throws IOException {
		int processCount = 0;
		for(Record r : rh) {
			log.trace("removing record: " + r.getID());
			if(namespace != null) {
				// log.trace("using namespace '"+namespace+"'");
			}
			ByteArrayInputStream bais = new ByteArrayInputStream(r.getData().getBytes());
			this.getJenaModel().remove(new MemJenaConnect(bais, namespace, null).getJenaModel());
			try {
				bais.close();
			} catch(IOException e) {
				// ignore
			}
			processCount++;
		}
		return processCount;
	}
	
	/**
	 * Adds all records in a RecordHandler to the model
	 * @param rh the RecordHandler to pull records from
	 * @param namespace the base uri to use for imported uris
	 * @return number of records added
	 */
	public int importRdfFromRH(RecordHandler rh, String namespace) {
		int processCount = 0;
		for(Record r : rh) {
			log.trace("loading record: " + r.getID());
			if(namespace != null) {
				// log.trace("using namespace '"+namespace+"'");
			}
			ByteArrayInputStream bais = new ByteArrayInputStream(r.getData().getBytes());
			this.getJenaModel().read(bais, namespace);
			try {
				bais.close();
			} catch(IOException e) {
				// ignore
			}
			processCount++;
		}
		return processCount;
	}
	
	/**
	 * Closes the model and the jdbc connection
	 */
	public abstract void close();
	
	/**
	 * Build a QueryExecution from a queryString
	 * @param queryString the query to build execution for
	 * @return the QueryExecution
	 */
	private QueryExecution buildQueryExec(String queryString) {
		return QueryExecutionFactory.create(QueryFactory.create(queryString, Syntax.syntaxARQ), getJenaModel());
	}
	
	/**
	 * Executes a sparql select query against the JENA model and returns the selected result set
	 * @param queryString the query to execute against the model
	 * @return the executed query result set
	 */
	public ResultSet executeSelectQuery(String queryString) {
		return buildQueryExec(queryString).execSelect();
	}
	
	/**
	 * Executes a sparql construct query against the JENA model and returns the constructed result model
	 * @param queryString the query to execute against the model
	 * @return the executed query result model
	 * @throws IOException error connecting
	 */
	public JenaConnect executeConstructQuery(String queryString) throws IOException {
		JenaConnect jc = new MemJenaConnect();
		jc.getJenaModel().add(buildQueryExec(queryString).execConstruct());
		return jc;
	}
	
	/**
	 * Executes a sparql describe query against the JENA model and returns the description result model
	 * @param queryString the query to execute against the model
	 * @return the executed query result model
	 * @throws IOException error connecting
	 */
	public JenaConnect executeDescribeQuery(String queryString) throws IOException {
		JenaConnect jc = new MemJenaConnect();
		jc.getJenaModel().add(buildQueryExec(queryString).execDescribe());
		return jc;
	}
	
	/**
	 * Executes a sparql describe query against the JENA model and returns the description result model
	 * @param queryString the query to execute against the model
	 * @return the executed query result model
	 */
	public boolean executeAskQuery(String queryString) {
		return buildQueryExec(queryString).execAsk();
	}
	
	/**
	 * Executes a sparql update query against the JENA model
	 * @param queryString the query to execute against the model
	 */
	public void executeUpdateQuery(String queryString) {
		this.jenaModel.begin();
		this.jenaModel.notifyEvent(GraphEvents.startRead);
		try {
			UpdateAction.execute(UpdateFactory.create(queryString), this.jenaModel);
		} finally {
			this.jenaModel.notifyEvent(GraphEvents.finishRead);
			this.jenaModel.commit();
		}
	}
	
	/**
	 * RDF formats
	 */
	protected static HashMap<String, ResultSetFormat> formatSymbols = new HashMap<String, ResultSetFormat>();
	static {
		formatSymbols.put(ResultSetFormat.syntaxXML.getSymbol(), ResultSetFormat.syntaxXML);
		formatSymbols.put(ResultSetFormat.syntaxRDF_XML.getSymbol(), ResultSetFormat.syntaxRDF_XML);
		formatSymbols.put(ResultSetFormat.syntaxRDF_N3.getSymbol(), ResultSetFormat.syntaxRDF_N3);
		formatSymbols.put(ResultSetFormat.syntaxCSV.getSymbol(), ResultSetFormat.syntaxCSV);
		formatSymbols.put(ResultSetFormat.syntaxText.getSymbol(), ResultSetFormat.syntaxText);
		formatSymbols.put(ResultSetFormat.syntaxJSON.getSymbol(), ResultSetFormat.syntaxJSON);
	}
	
	/**
	 * Execute a Query and output result to System.out
	 * @param queryParam the query
	 * @param resultFormatParam the format to return the results in ('RS_RDF',etc for select queries / 'RDF/XML',etc for
	 * construct/describe queries)
	 * @throws IOException error writing to output
	 */
	public void executeQuery(String queryParam, String resultFormatParam) throws IOException {
		executeQuery(queryParam, resultFormatParam, System.out);
	}
	
	/**
	 * Execute a Query
	 * @param queryParam the query
	 * @param resultFormatParam the format to return the results in ('RS_TEXT' default for select queries / 'RDF/XML' default for construct/describe queries)
	 * @param output output stream to write to - null uses System.out
	 * @throws IOException error writing to output
	 */
	public void executeQuery(String queryParam, String resultFormatParam, OutputStream output) throws IOException {
		OutputStream out = (output != null)?output:System.out;
		QueryExecution qe = null;
		try {
			Query query = QueryFactory.create(queryParam, Syntax.syntaxARQ);
			qe = QueryExecutionFactory.create(query, getJenaModel());
			if(query.isSelectType()) {
				ResultSetFormat rsf = formatSymbols.get(resultFormatParam);
				if(rsf == null) {
					rsf = ResultSetFormat.syntaxText;
				}
				ResultSetFormatter.output(out, qe.execSelect(), rsf);
			} else if(query.isAskType()) {
				out.write(Boolean.toString(qe.execAsk()).getBytes());
			} else {
				Model resultModel = null;
				if(query.isConstructType()) {
					resultModel = qe.execConstruct();
				} else if(query.isDescribeType()) {
					resultModel = qe.execDescribe();
				} else {
					throw new IllegalArgumentException("Query Invalid: Not Select, Construct, Ask, or Describe");
				}
				
				resultModel.write(out, resultFormatParam);
			}
		} finally {
			if(qe != null) {
				qe.close();
			}
		}
	}
	
	/**
	 * Accessor for Jena Model
	 * @return the Jena Model
	 */
	public Model getJenaModel() {
		return this.jenaModel;
	}
	
	/**
	 * Setter
	 * @param jena the new model
	 */
	protected void setJenaModel(Model jena) {
		this.jenaModel = jena;
	}
	
	/**
	 * Checks if the model contains the given uri
	 * @param uri the uri to check for
	 * @return true if found, false otherwise
	 */
	public boolean containsURI(String uri) {
		String query =	"ASK " +
						"{ " +
							"<" + uri + "> ?p ?o " +
						"}";
//		log.debug(query); 
		return executeAskQuery(query);
	}
	
	/**
	 * Get the ArgParser for this task
	 * @return the ArgParser
	 */
	private static ArgParser getParser() {
		ArgParser parser = new ArgParser("JenaConnect");
		parser.addArgument(new ArgDef().setShortOption('j').setLongOpt("jena").withParameter(true, "CONFIG_FILE").setDescription("config file for jena model").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('J').setLongOpt("jenaOverride").withParameterValueMap("JENA_PARAM", "VALUE").setDescription("override the JENA_PARAM of jena model config using VALUE").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('q').setLongOpt("query").withParameter(true, "SPARQL_QUERY").setDescription("sparql query to execute").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('Q').setLongOpt("queryResultFormat").withParameter(true, "RESULT_FORMAT").setDescription("the format to return the results in ('RS_RDF',etc for select queries / 'RDF/XML',etc for construct/describe queries)").setRequired(false));
		return parser;
	}
	
	/**
	 * Main method
	 * @param args commandline arguments
	 */
	public static void main(String... args) {
		InitLog.initLogger(JenaConnect.class);
		try {
			ArgList argList = new ArgList(getParser(), args);
			JenaConnect jc = JenaConnect.parseConfig(argList.get("j"), argList.getValueMap("J"));
			jc.executeQuery(argList.get("q"), argList.get("Q"));
		} catch(IllegalArgumentException e) {
			log.error(e.getMessage(), e);
			System.err.println(getParser().getUsage());
		} catch(Exception e) {
			log.error(e.getMessage(), e);
		}
	}
	
	/**
	 * Config parser for Jena Models
	 * @author Christopher Haines (hainesc@ctrip.ufl.edu)
	 */
	private static class JenaConnectConfigParser extends DefaultHandler {
		/**
		 * Param list from the config file
		 */
		private Map<String,String> params;
		/**
		 * temporary storage for cdata
		 */
		private String tempVal;
		/**
		 * temporary storage for param name
		 */
		private String tempParamName;
		
		/**
		 * Default Constructor
		 */
		protected JenaConnectConfigParser() {
			this.params = new HashMap<String, String>();
			this.tempVal = "";
			this.tempParamName = "";
		}
		
		/**
		 * Build a JenaConnect using the input stream data
		 * @param inputStream stream to read config from
		 * @return the JenaConnect described by the stream
		 * @throws IOException error reading stream
		 */
		protected Map<String,String> parseConfig(InputStream inputStream) throws IOException {
			if(inputStream != null) {
				// get a factory
				SAXParserFactory spf = SAXParserFactory.newInstance();
				try {
					// get a new instance of parser
					SAXParser sp = spf.newSAXParser();
					// parse the file and also register this class for call backs
					sp.parse(inputStream, this);
				} catch(SAXException e) {
					throw new IOException(e.getMessage(), e);
				} catch(ParserConfigurationException e) {
					throw new IOException(e.getMessage(), e);
				}
			}
			return this.params;
		}
		
		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			this.tempVal = "";
			this.tempParamName = "";
			if(qName.equalsIgnoreCase("Param")) {
				this.tempParamName = attributes.getValue("name");
			} else if(!qName.equalsIgnoreCase("Model")) {
				throw new SAXException("Unknown Tag: " + qName);
			}
		}
		
		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			this.tempVal = new String(ch, start, length);
		}
		
		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
			if(qName.equalsIgnoreCase("Param")) {
				this.params.put(this.tempParamName, this.tempVal);
			} else if(!qName.equalsIgnoreCase("Model")) {
				throw new SAXException("Unknown Tag: " + qName);
			}
		}
	}

	/**
	 * Remove all statements from model
	 */
	public abstract void truncate();

	/**
	 * Set the modelName
	 * @param modelName the model name
	 */
	protected void setModelName(String modelName) {
		this.modelName = modelName;
	}

	/**
	 * Get the modelName
	 * @return the modelName
	 */
	public String getModelName() {
		return this.modelName;
	}
}
