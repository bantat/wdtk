package org.wikidata.wdtk.rdf;

/*
 * #%L
 * Wikidata Toolkit RDF
 * %%
 * Copyright (C) 2014 Wikidata Toolkit Developers
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.openrdf.model.Resource;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.rio.RDFHandlerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikidata.wdtk.datamodel.interfaces.Claim;
import org.wikidata.wdtk.datamodel.interfaces.EntityIdValue;
import org.wikidata.wdtk.datamodel.interfaces.ItemDocument;
import org.wikidata.wdtk.datamodel.interfaces.MonolingualTextValue;
import org.wikidata.wdtk.datamodel.interfaces.PropertyDocument;
import org.wikidata.wdtk.datamodel.interfaces.PropertyIdValue;
import org.wikidata.wdtk.datamodel.interfaces.Reference;
import org.wikidata.wdtk.datamodel.interfaces.SiteLink;
import org.wikidata.wdtk.datamodel.interfaces.Sites;
import org.wikidata.wdtk.datamodel.interfaces.Snak;
import org.wikidata.wdtk.datamodel.interfaces.SnakGroup;
import org.wikidata.wdtk.datamodel.interfaces.Statement;
import org.wikidata.wdtk.datamodel.interfaces.StatementDocument;
import org.wikidata.wdtk.datamodel.interfaces.StatementGroup;
import org.wikidata.wdtk.datamodel.interfaces.StatementRank;
import org.wikidata.wdtk.datamodel.interfaces.TermedDocument;
import org.wikidata.wdtk.datamodel.interfaces.ValueSnak;
import org.wikidata.wdtk.datamodel.interfaces.WikimediaLanguageCodes;
import org.wikidata.wdtk.rdf.values.AnyValueConverter;

/**
 * This class provides functions to convert objects of wdtk-datamodel in a rdf
 * graph.
 *
 * @author Michael Günther
 *
 */
public class RdfConverter {

	static final Logger logger = LoggerFactory.getLogger(RdfConverter.class);

	final RdfWriter rdfWriter;
	final AnyValueConverter valueRdfConverter;
	final SnakRdfConverter snakRdfConverter;
	final OwlDeclarationBuffer owlDeclarationBuffer;
	final ReferenceRdfConverter referenceRdfConverter;
	final PropertyRegister propertyRegister;
	final Sites sites;

	int tasks = RdfSerializer.TASK_ALL_ENTITIES
			| RdfSerializer.TASK_ALL_EXACT_DATA;

	public RdfConverter(RdfWriter rdfWriter, Sites sites,
			PropertyRegister propertyRegister) {
		this.sites = sites;
		this.rdfWriter = rdfWriter;
		this.propertyRegister = propertyRegister;

		this.owlDeclarationBuffer = new OwlDeclarationBuffer();
		this.valueRdfConverter = new AnyValueConverter(rdfWriter,
				this.owlDeclarationBuffer, this.propertyRegister);
		this.snakRdfConverter = new SnakRdfConverter(rdfWriter,
				this.owlDeclarationBuffer, this.propertyRegister,
				this.valueRdfConverter);
		this.referenceRdfConverter = new ReferenceRdfConverter(rdfWriter,
				this.snakRdfConverter, this.propertyRegister.siteUri);
	}

	/**
	 * Sets the tasks that should be performed during export. The value should
	 * be a combination of flags such as {@link RdfSerializer#TASK_STATEMENTS}.
	 *
	 * @param tasks
	 *            the tasks to be performed
	 */
	public void setTasks(int tasks) {
		this.tasks = tasks;
	}

	/**
	 * Returns the tasks that should be performed during export. The value
	 * should be a combination of flags such as
	 * {@link RdfSerializer#TASK_STATEMENTS}.
	 *
	 * @return tasks to be performed
	 */
	public int getTasks() {
		return this.tasks;
	}

	/**
	 * Writes OWL declarations for all basic vocabulary elements used in the
	 * dump.
	 *
	 * @throws RDFHandlerException
	 */
	public void writeBasicDeclarations() throws RDFHandlerException {
		for (Map.Entry<String, String> uriType : Vocabulary
				.getKnownVocabularyTypes().entrySet()) {
			this.rdfWriter.writeTripleUriObject(uriType.getKey(),
					RdfWriter.RDF_TYPE, uriType.getValue());
		}
	}

	public void writeNamespaceDeclarations() throws RDFHandlerException {
		this.rdfWriter.writeNamespaceDeclaration("id",
				this.propertyRegister.getUriPrefix());
		this.rdfWriter
				.writeNamespaceDeclaration("wo", Vocabulary.PREFIX_WBONTO);
		this.rdfWriter.writeNamespaceDeclaration("rdf", Vocabulary.PREFIX_RDF);
		this.rdfWriter
				.writeNamespaceDeclaration("rdfs", Vocabulary.PREFIX_RDFS);
		this.rdfWriter.writeNamespaceDeclaration("owl", Vocabulary.PREFIX_OWL);
		this.rdfWriter.writeNamespaceDeclaration("xsd", Vocabulary.PREFIX_XSD);
		this.rdfWriter.writeNamespaceDeclaration("schema",
				Vocabulary.PREFIX_SCHEMA);
		this.rdfWriter
				.writeNamespaceDeclaration("skos", Vocabulary.PREFIX_SKOS);
		this.rdfWriter
				.writeNamespaceDeclaration("prov", Vocabulary.PREFIX_PROV);
	}

	public void writeItemDocument(ItemDocument document)
			throws RDFHandlerException {

		if (!hasTask(RdfSerializer.TASK_ITEMS)) {
			return;
		}

		String subjectUri = document.getEntityId().getIri();
		Resource subject = this.rdfWriter.getUri(subjectUri);

		if ((this.tasks & (RdfSerializer.TASK_ALL_EXACT_DATA | RdfSerializer.TASK_SIMPLE_STATEMENTS)) != 0) {
			this.rdfWriter.writeTripleValueObject(subject, RdfWriter.RDF_TYPE,
					RdfWriter.WB_ITEM);
		}

		writeDocumentTerms(subject, document);

		if (hasTask(RdfSerializer.TASK_SIMPLE_STATEMENTS)) {
			writeSimpleStatements(subject, document);
		}

		if (hasTask(RdfSerializer.TASK_INSTANCE_OF)) {
			writeInstanceOfStatements(subject, document);
		}

		if (hasTask(RdfSerializer.TASK_TAXONOMY)) {
			writeSubclassOfStatements(subject, document);
		}

		if (hasTask(RdfSerializer.TASK_STATEMENTS)) {
			writeStatements(subject, document);
		}

		writeSiteLinks(subject, document.getSiteLinks());

		if (hasTask(RdfSerializer.TASK_TAXONOMY)) {
			this.owlDeclarationBuffer.writeClassDeclarations(this.rdfWriter);
		}

		this.snakRdfConverter.writeAuxiliaryTriples();
		this.owlDeclarationBuffer.writePropertyDeclarations(this.rdfWriter,
				hasTask(RdfSerializer.TASK_STATEMENTS),
				hasTask(RdfSerializer.TASK_SIMPLE_STATEMENTS));
		this.referenceRdfConverter.writeReferences();
	}

	public void writePropertyDocument(PropertyDocument document)
			throws RDFHandlerException {

		propertyRegister.setPropertyType(document.getPropertyId(), document
				.getDatatype().getIri());

		if (!hasTask(RdfSerializer.TASK_PROPERTIES)) {
			return;
		}

		String propertyUri = document.getEntityId().getIri();
		Resource subject = this.rdfWriter.getUri(propertyUri);

		this.rdfWriter.writeTripleValueObject(subject, RdfWriter.RDF_TYPE,
				RdfWriter.WB_PROPERTY);

		writeDocumentTerms(subject, document);

		if (hasTask(RdfSerializer.TASK_DATATYPES)) {
			this.rdfWriter.writeTripleValueObject(subject,
					RdfWriter.WB_PROPERTY_TYPE,
					this.rdfWriter.getUri(document.getDatatype().getIri()));
		}

		if (hasTask(RdfSerializer.TASK_STATEMENTS)) {
			writeStatements(subject, document);
		}

		if (hasTask(RdfSerializer.TASK_PROPERTY_LINKS)) {
			writeInterPropertyLinks(document);

		}

		if (hasTask(RdfSerializer.TASK_SUBPROPERTIES)) {
			writeSubpropertyOfStatements(subject, document);
		}

		this.snakRdfConverter.writeAuxiliaryTriples();
		this.owlDeclarationBuffer.writePropertyDeclarations(this.rdfWriter,
				hasTask(RdfSerializer.TASK_STATEMENTS),
				hasTask(RdfSerializer.TASK_SIMPLE_STATEMENTS));
		this.referenceRdfConverter.writeReferences();
	}

	/**
	 * Writes triples which conect properties with there corresponding rdf
	 * properties for statements, simple statements, qualifiers, reference
	 * attributes and values.
	 *
	 * @param document
	 * @throws RDFHandlerException
	 */
	void writeInterPropertyLinks(PropertyDocument document)
			throws RDFHandlerException {
		Resource subject = this.rdfWriter.getUri(document.getEntityId()
				.getIri());
		this.rdfWriter
				.writeTripleUriObject(subject, this.rdfWriter
						.getUri(Vocabulary.WB_PROPERTY_STATEMENT_LINKAGE),
						Vocabulary.getPropertyUri(document.getPropertyId(),
								PropertyContext.STATEMENT));
		this.rdfWriter.writeTripleUriObject(subject, this.rdfWriter
				.getUri(Vocabulary.WB_PROPERTY_QUALTIFIER_LINKAGE), Vocabulary
				.getPropertyUri(document.getPropertyId(),
						PropertyContext.QUALIFIER));
		this.rdfWriter
				.writeTripleUriObject(subject, this.rdfWriter
						.getUri(Vocabulary.WB_PROPERTY_REFERENCE_LINKAGE),
						Vocabulary.getPropertyUri(document.getPropertyId(),
								PropertyContext.REFERENCE));
		this.rdfWriter.writeTripleUriObject(subject, this.rdfWriter
				.getUri(Vocabulary.WB_PROPERTY_SIMPLE_CLAIM), Vocabulary
				.getPropertyUri(document.getPropertyId(),
						PropertyContext.SIMPLE_CLAIM));
		this.rdfWriter.writeTripleUriObject(subject, this.rdfWriter
				.getUri(Vocabulary.WB_PROPERTY_VALUE_LINKAGE),
				Vocabulary.getPropertyUri(document.getPropertyId(),
						PropertyContext.VALUE));
	}

	void writeStatements(Resource subject, StatementDocument statementDocument)
			throws RDFHandlerException {
		for (StatementGroup statementGroup : statementDocument
				.getStatementGroups()) {
			URI property = this.rdfWriter.getUri(Vocabulary.getPropertyUri(
					statementGroup.getProperty(), PropertyContext.STATEMENT));
			for (Statement statement : statementGroup.getStatements()) {
				this.rdfWriter.writeTripleUriObject(subject, property,
						Vocabulary.getStatementUri(statement));
			}
		}

		for (StatementGroup statementGroup : statementDocument
				.getStatementGroups()) {
			for (Statement statement : statementGroup.getStatements()) {
				writeStatement(statement);
			}
		}
	}

	void writeSimpleStatements(Resource subject,
			StatementDocument statementDocument) {
		for (StatementGroup statementGroup : statementDocument
				.getStatementGroups()) {
			for (Statement statement : statementGroup.getStatements()) {
				if (statement.getClaim().getQualifiers().size() == 0) {
					this.snakRdfConverter.setSnakContext(subject,
							PropertyContext.SIMPLE_CLAIM);
					statement.getClaim().getMainSnak()
							.accept(this.snakRdfConverter);
				}
			}
		}
	}

	void writeInstanceOfStatements(Resource subject, ItemDocument itemDocument) {
		for (StatementGroup statementGroup : itemDocument.getStatementGroups()) {
			if (!"P31".equals(statementGroup.getProperty().getId())) {
				continue;
			}
			for (Statement statement : statementGroup.getStatements()) {
				if (statement.getClaim().getMainSnak() instanceof ValueSnak
						&& statement.getClaim().getQualifiers().size() == 0) {

					ValueSnak mainSnak = (ValueSnak) statement.getClaim()
							.getMainSnak();
					Value value = this.valueRdfConverter
							.getRdfValue(mainSnak.getValue(),
									mainSnak.getPropertyId(), true);
					if (value == null) {
						logger.error("Could not serialize instance of snak: missing value (Snak: "
								+ mainSnak.toString() + ")");
						continue;
					}

					try {
						this.rdfWriter.writeTripleValueObject(subject,
								RdfWriter.RDF_TYPE, value);
					} catch (RDFHandlerException e) {
						throw new RuntimeException(e.toString(), e);
					}
				}
			}
		}
	}

	void writeSubclassOfStatements(Resource subject, ItemDocument itemDocument) {
		for (StatementGroup statementGroup : itemDocument.getStatementGroups()) {
			boolean isSubClassOf = "P279".equals(statementGroup.getProperty()
					.getId());
			boolean isInstanceOf = "P31".equals(statementGroup.getProperty()
					.getId());
			if (!isInstanceOf && !isSubClassOf) {
				continue;
			}
			for (Statement statement : statementGroup.getStatements()) {
				if (statement.getClaim().getMainSnak() instanceof ValueSnak) {
					ValueSnak mainSnak = (ValueSnak) statement.getClaim()
							.getMainSnak();

					if (isSubClassOf) {
						this.owlDeclarationBuffer.addClass(itemDocument
								.getEntityId());
					}
					if (mainSnak.getValue() instanceof EntityIdValue) {
						this.owlDeclarationBuffer
								.addClass((EntityIdValue) mainSnak.getValue());
					}

					if (statement.getClaim().getQualifiers().size() == 0
							&& isSubClassOf) {
						Value value = this.valueRdfConverter.getRdfValue(
								mainSnak.getValue(), mainSnak.getPropertyId(),
								true);
						if (value == null) {
							logger.error("Could not serialize subclass of snak: missing value (Snak: "
									+ mainSnak.toString() + ")");
							continue;
						}

						try {
							this.rdfWriter.writeTripleValueObject(subject,
									RdfWriter.RDFS_SUBCLASS_OF, value);
						} catch (RDFHandlerException e) {
							throw new RuntimeException(e.toString(), e);
						}
					}
				}
			}
		}
	}

	void writeSubpropertyOfStatements(Resource subject,
			PropertyDocument propertyDocument) {
		for (StatementGroup statementGroup : propertyDocument
				.getStatementGroups()) {
			boolean isSubPropertyOf = "P1647".equals(statementGroup
					.getProperty().getId());
			if (!isSubPropertyOf) {
				continue;
			}

			for (Statement statement : statementGroup.getStatements()) {
				if (statement.getClaim().getMainSnak() instanceof ValueSnak) {
					ValueSnak mainSnak = (ValueSnak) statement.getClaim()
							.getMainSnak();
					if (statement.getClaim().getQualifiers().size() == 0) {
						Value value = this.valueRdfConverter.getRdfValue(
								mainSnak.getValue(), mainSnak.getPropertyId(),
								true);
						if (value == null) {
							logger.error("Clould not serialize subclass of snak: missing value (Snak: "
									+ mainSnak.toString() + ")");
							continue;
						}
						if (mainSnak.getValue() instanceof EntityIdValue) {
							String id = ((EntityIdValue) mainSnak.getValue())
									.getId();
							if (id.startsWith("P")) {
								String datatype = this.propertyRegister
										.getPropertyType((PropertyIdValue) mainSnak
												.getValue());
								if (!propertyDocument.getDatatype().getIri()
										.equals(datatype)) {
									logger.warn("Datatype of subproperty "
											+ propertyDocument.getDatatype()
													.toString()
											+ " is different from superproperty "
											+ datatype);
									continue;
								}
							} else {
								logger.warn(value.toString()
										+ " is not a Property");
							}
						} else {
							logger.warn("Not a valid EntityIdValue: "
									+ value.toString());
							continue;
						}

						try {
							this.rdfWriter.writeTripleValueObject(subject,
									RdfWriter.RDFS_SUBPROPERTY_OF, value);
						} catch (RDFHandlerException e) {
							throw new RuntimeException(e.toString(), e);
						}
					}

				}
			}
		}
	}

	void writeDocumentTerms(Resource subject, TermedDocument document)
			throws RDFHandlerException {
		if (hasTask(RdfSerializer.TASK_LABELS)) {
			writeTermTriples(subject, RdfWriter.RDFS_LABEL, document
					.getLabels().values());
		}
		if (hasTask(RdfSerializer.TASK_DESCRIPTIONS)) {
			writeTermTriples(subject, RdfWriter.SCHEMA_DESCRIPTION, document
					.getDescriptions().values());
		}
		if (hasTask(RdfSerializer.TASK_ALIASES)) {
			for (List<MonolingualTextValue> aliases : document.getAliases()
					.values()) {
				writeTermTriples(subject, RdfWriter.SKOS_ALT_LABEL, aliases);
			}
		}
	}

	void writeTermTriples(Resource subject, URI predicate,
			Collection<MonolingualTextValue> terms) throws RDFHandlerException {
		for (MonolingualTextValue mtv : terms) {
			this.rdfWriter.writeTripleValueObject(subject, predicate,
					RdfConverter.getMonolingualTextValueLiteral(mtv,
							this.rdfWriter));
		}
	}

	/**
	 * Writes a triple for the {@link StatementRank} of a {@link Statement} to
	 * the dump.
	 *
	 * @param subject
	 * @param rank
	 */
	void writeStatementRankTriple(Resource subject, StatementRank rank) {
		try {
			this.rdfWriter.writeTripleUriObject(subject, RdfWriter.WB_RANK,
					getUriStringForRank(rank));
		} catch (RDFHandlerException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	void writeStatement(Statement statement) throws RDFHandlerException {
		String statementUri = Vocabulary.getStatementUri(statement);
		Resource statementResource = this.rdfWriter.getUri(statementUri);

		this.rdfWriter.writeTripleValueObject(statementResource,
				RdfWriter.RDF_TYPE, RdfWriter.WB_STATEMENT);
		writeClaim(statementResource, statement.getClaim());

		writeReferences(statementResource, statement.getReferences());

		writeStatementRankTriple(statementResource, statement.getRank());

	}

	void writeReferences(Resource statementResource,
			List<? extends Reference> references) throws RDFHandlerException {
		for (Reference reference : references) {
			Resource resource = this.referenceRdfConverter
					.addReference(reference);
			this.rdfWriter.writeTripleValueObject(statementResource,
					RdfWriter.PROV_WAS_DERIVED_FROM, resource);
		}
	}

	void writeClaim(Resource claimResource, Claim claim) {
		this.snakRdfConverter.setSnakContext(claimResource,
				PropertyContext.VALUE);
		claim.getMainSnak().accept(this.snakRdfConverter);
		this.snakRdfConverter.setSnakContext(claimResource,
				PropertyContext.QUALIFIER);
		for (SnakGroup snakGroup : claim.getQualifiers()) {
			for (Snak snak : snakGroup.getSnaks()) {
				snak.accept(this.snakRdfConverter);
			}
		}
	}

	void writeSiteLinks(Resource subject, Map<String, SiteLink> siteLinks)
			throws RDFHandlerException {

		if (!hasTask(RdfSerializer.TASK_SITELINKS)) {
			return;
		}

		for (String key : siteLinks.keySet()) {
			SiteLink siteLink = siteLinks.get(key);
			String siteLinkUrl = this.sites.getSiteLinkUrl(siteLink);
			if (siteLinkUrl != null) {
				URI siteLinkUri = this.rdfWriter.getUri(siteLinkUrl);

				this.rdfWriter.writeTripleValueObject(siteLinkUri,
						RdfWriter.RDF_TYPE, RdfWriter.WB_ARTICLE);
				this.rdfWriter.writeTripleValueObject(siteLinkUri,
						RdfWriter.SCHEMA_ABOUT, subject);
				// Commons has no uniform language; don't export
				if (!"commonswiki".equals(siteLink.getSiteKey())) {
					String siteLanguageCode = this.sites
							.getLanguageCode(siteLink.getSiteKey());
					String languageCode;
					try {
						languageCode = WikimediaLanguageCodes
								.getLanguageCode(siteLanguageCode);
					} catch (IllegalArgumentException e) {
						languageCode = siteLanguageCode;
						logger.warn("Unknown Wikimedia language code \""
								+ languageCode
								+ "\". Using this code in RDF now, but this might be wrong.");
					}

					this.rdfWriter.writeTripleStringObject(siteLinkUri,
							RdfWriter.SCHEMA_IN_LANGUAGE, languageCode);
				}
			} else {
				logger.warn("Failed to find URL for page \""
						+ siteLink.getPageTitle() + "\" on site \""
						+ siteLink.getSiteKey() + "\"");
			}
		}
	}

	/**
	 *
	 * @param value
	 * @return
	 */
	public static Value getMonolingualTextValueLiteral(
			MonolingualTextValue value, RdfWriter rdfWriter) {
		String languageCode;
		try {
			languageCode = WikimediaLanguageCodes.getLanguageCode(value
					.getLanguageCode());
		} catch (IllegalArgumentException e) {
			languageCode = value.getLanguageCode();
			logger.warn("Unknown Wikimedia language code \""
					+ languageCode
					+ "\". Using this code in RDF now, but this might be wrong.");
		}
		return rdfWriter.getLiteral(value.getText(), languageCode);
	}

	/**
	 * Checks if the given task (or set of tasks) is to be performed.
	 *
	 * @param task
	 *            the task (or set of tasks) to be checked
	 * @return true if the tasks include the given task
	 */
	boolean hasTask(int task) {
		return ((this.tasks & task) == task);
	}

	/**
	 * Returns an URI which represents the statement rank in a triple.
	 *
	 * @param rank
	 * @return
	 */
	String getUriStringForRank(StatementRank rank) {
		switch (rank) {
		case NORMAL:
			return Vocabulary.WB_NORMAL_RANK;
		case PREFERRED:
			return Vocabulary.WB_PREFERRED_RANK;
		case DEPRECATED:
			return Vocabulary.WB_DEPRECATED_RANK;
		default:
			throw new IllegalArgumentException();
		}
	}

}
