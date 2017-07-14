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
import java.util.ArrayList;
import java.util.List;

import org.openrdf.model.Resource;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.rio.RDFHandlerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikidata.wdtk.datamodel.interfaces.DatatypeIdValue;
import org.wikidata.wdtk.datamodel.interfaces.NoValueSnak;
import org.wikidata.wdtk.datamodel.interfaces.PropertyIdValue;
import org.wikidata.wdtk.datamodel.interfaces.Snak;
import org.wikidata.wdtk.datamodel.interfaces.SnakVisitor;
import org.wikidata.wdtk.datamodel.interfaces.SomeValueSnak;
import org.wikidata.wdtk.datamodel.interfaces.ValueSnak;
import org.wikidata.wdtk.rdf.extensions.ExportExtensions;
import org.wikidata.wdtk.rdf.values.AnyValueConverter;

/**
 * Class to convert Wikibase snaks to RDF. The main entry point for this class
 * is {@link #writeSnak(Snak, Resource, PropertyContext)}. Alternatively, one
 * can use {@link #setSnakContext(Resource, PropertyContext)} and use the class
 * as a visitor. {@link SomeValueSnak} and {@link NoValueSnak} require further
 * RDF triples to be written; the same is true for some complex data values that
 * might be used with {@link ValueSnak}. In such cases, the class stores the
 * values to a buffer. Methods for writing additional triples for these buffered
 * values can be called later.
 *
 * @author Markus Kroetzsch
 *
 */
public class SnakRdfConverter implements SnakVisitor<Void> {

	/**
	 * Local value class for storing information about property restrictions.
	 *
	 * @author Markus Kroetzsch
	 *
	 */
	private class PropertyRestriction {

		final Resource subject;
		final String propertyUri;
		final String rangeUri;

		PropertyRestriction(Resource subject, String propertyUri,
				String rangeUri) {
			this.subject = subject;
			this.propertyUri = propertyUri;
			this.rangeUri = rangeUri;
		}
	}

	static final Logger logger = LoggerFactory
			.getLogger(SnakRdfConverter.class);

	final AnyValueConverter valueRdfConverter;

	final RdfWriter rdfWriter;
	final PropertyRegister propertyRegister;
	final OwlDeclarationBuffer rdfConversionBuffer;
	final ExportExtensions exportExtensions;

	final List<PropertyRestriction> someValuesQueue;
	final List<PropertyRestriction> noValuesQueue;

	Resource currentSubject;
	PropertyContext currentPropertyContext;
	boolean simple;

	public SnakRdfConverter(RdfWriter rdfWriter,
			OwlDeclarationBuffer owlDeclarationBuffer,
			PropertyRegister propertyRegister,
			AnyValueConverter valueRdfConverter) {
		this.rdfWriter = rdfWriter;
		this.rdfConversionBuffer = owlDeclarationBuffer;
		this.propertyRegister = propertyRegister;
		this.valueRdfConverter = valueRdfConverter;
		this.exportExtensions = new ExportExtensions(rdfWriter,
				owlDeclarationBuffer, propertyRegister);
		ExportExtensions
				.registerWikidataExportExtensions(this.exportExtensions);

		this.someValuesQueue = new ArrayList<PropertyRestriction>();
		this.noValuesQueue = new ArrayList<PropertyRestriction>();
	}

	/**
	 * Writes the given snak for the given subject. The context defines if the
	 * snak is used as a main snak, qualifier, or in a reference. Some data
	 * might be buffered instead of being written immediately. The method
	 * {@link #writeAuxiliaryTriples()} needs to be called to serialize this
	 * additional data later on.
	 *
	 * @param snak
	 *            the snake to write
	 * @param subject
	 *            the resource that should be used as a subject of the serialied
	 *            triples
	 * @param propertyContext
	 *            the context in which the snak is used
	 */
	public void writeSnak(Snak snak, Resource subject,
			PropertyContext propertyContext) {
		setSnakContext(subject, propertyContext);
		snak.accept(this);
	}

	/**
	 * Sets the context in which snaks should be used. This is useful when
	 * converting many snaks that have the same context. In this case, one can
	 * set the context manually and use the converter as a {@link SnakVisitor}.
	 *
	 * @param subject
	 *            the resource that should be used as a subject of the serialied
	 *            triples
	 * @param propertyContext
	 *            the context in which the snaks that are to be converted are
	 *            used
	 */
	public void setSnakContext(Resource subject, PropertyContext propertyContext) {
		this.currentSubject = subject;
		this.currentPropertyContext = propertyContext;
		this.simple = (this.currentPropertyContext == PropertyContext.SIMPLE_CLAIM);
	}

	@Override
	public Void visit(ValueSnak snak) {
		String propertyUri = Vocabulary.getPropertyUri(snak.getPropertyId(),
				this.currentPropertyContext);
		URI property = this.rdfWriter.getUri(propertyUri);
		Value value = valueRdfConverter.getRdfValue(snak.getValue(),
				snak.getPropertyId(), this.simple);
		if (value == null) {
			logger.error("Could not serialize snak: missing value (Snak: "
					+ snak.toString() + ")");
			return null;
		}

		try {
			this.rdfWriter.writeTripleValueObject(this.currentSubject,
					property, value);
		} catch (RDFHandlerException e) {
			throw new RuntimeException(e.toString(), e);
		}

		this.exportExtensions.writeValueSnakExtensions(snak,
				this.currentSubject);

		return null;
	}

	@Override
	public Void visit(SomeValueSnak snak) {
		String rangeUri = getRangeUri(snak.getPropertyId());
		if (rangeUri == null) {
			logger.error("Count not export SomeValueSnak for property "
					+ snak.getPropertyId().getId() + ": OWL range not known.");
			return null;
		}

		String propertyUri = Vocabulary.getPropertyUri(snak.getPropertyId(),
				this.currentPropertyContext);
		Resource bnode = this.rdfWriter.getFreshBNode();
		addSomeValuesRestriction(bnode, propertyUri, rangeUri);
		try {
			this.rdfWriter.writeTripleValueObject(this.currentSubject,
					RdfWriter.RDF_TYPE, bnode);
		} catch (RDFHandlerException e) {
			throw new RuntimeException(e.toString(), e);
		}

		return null;
	}

	@Override
	public Void visit(NoValueSnak snak) {
		String rangeUri = getRangeUri(snak.getPropertyId());
		if (rangeUri == null) {
			logger.error("Count not export NoValueSnak for property "
					+ snak.getPropertyId().getId() + ": OWL range not known.");
			return null;
		} else if (!Vocabulary.OWL_THING.equals(rangeUri)) {
			rangeUri = Vocabulary.RDFS_LITERAL;
		}

		String propertyUri = Vocabulary.getPropertyUri(snak.getPropertyId(),
				this.currentPropertyContext);
		Resource bnode = this.rdfWriter.getFreshBNode();
		addNoValuesRestriction(bnode, propertyUri, rangeUri);
		try {
			this.rdfWriter.writeTripleValueObject(this.currentSubject,
					RdfWriter.RDF_TYPE, bnode);
		} catch (RDFHandlerException e) {
			throw new RuntimeException(e.toString(), e);
		}

		return null;
	}

	/**
	 * Writes all auxiliary triples that have been buffered recently. This
	 * includes OWL property restrictions but it also includes any auxiliary
	 * triples required by complex values that were used in snaks.
	 *
	 * @throws RDFHandlerException
	 *             if there was a problem writing the RDF triples
	 */
	public void writeAuxiliaryTriples() throws RDFHandlerException {
		for (PropertyRestriction pr : this.someValuesQueue) {
			writeSomeValueRestriction(pr.propertyUri, pr.rangeUri, pr.subject);
		}
		this.someValuesQueue.clear();

		for (PropertyRestriction pr : this.noValuesQueue) {
			writeNoValueRestriction(pr.propertyUri, pr.rangeUri, pr.subject);
		}
		this.noValuesQueue.clear();

		this.valueRdfConverter.writeAuxiliaryTriples();
	}

	/**
	 * Writes a buffered some-value restriction.
	 *
	 * @param propertyUri
	 *            URI of the property to which the restriction applies
	 * @param rangeUri
	 *            URI of the class or datatype to which the restriction applies
	 * @param bnode
	 *            blank node representing the restriction
	 * @throws RDFHandlerException
	 *             if there was a problem writing the RDF triples
	 */
	void writeSomeValueRestriction(String propertyUri, String rangeUri,
			Resource bnode) throws RDFHandlerException {
		this.rdfWriter.writeTripleValueObject(bnode, RdfWriter.RDF_TYPE,
				RdfWriter.OWL_RESTRICTION);
		this.rdfWriter.writeTripleUriObject(bnode, RdfWriter.OWL_ON_PROPERTY,
				propertyUri);
		this.rdfWriter.writeTripleUriObject(bnode,
				RdfWriter.OWL_SOME_VALUES_FROM, rangeUri);
	}

	/**
	 * Writes a buffered no-value restriction.
	 *
	 * @param propertyUri
	 *            URI of the property to which the restriction applies
	 * @param rangeUri
	 *            URI of the class or datatype to which the restriction applies
	 * @param bnode
	 *            blank node representing the restriction
	 * @throws RDFHandlerException
	 *             if there was a problem writing the RDF triples
	 */
	void writeNoValueRestriction(String propertyUri, String rangeUri,
			Resource bnode) throws RDFHandlerException {

		Resource bnodeSome = this.rdfWriter.getFreshBNode();
		this.rdfWriter.writeTripleValueObject(bnode, RdfWriter.RDF_TYPE,
				RdfWriter.OWL_CLASS);
		this.rdfWriter.writeTripleValueObject(bnode,
				RdfWriter.OWL_COMPLEMENT_OF, bnodeSome);
		this.rdfWriter.writeTripleValueObject(bnodeSome, RdfWriter.RDF_TYPE,
				RdfWriter.OWL_RESTRICTION);
		this.rdfWriter.writeTripleUriObject(bnodeSome,
				RdfWriter.OWL_ON_PROPERTY, propertyUri);
		this.rdfWriter.writeTripleUriObject(bnodeSome,
				RdfWriter.OWL_SOME_VALUES_FROM, rangeUri);
	}

	/**
	 * Returns the class of datatype URI that best characterizes the range of
	 * the given property based on its datatype.
	 *
	 * @param propertyIdValue
	 *            the property for which to get a range
	 * @return the range URI or null if the datatype could not be identified.
	 */
	String getRangeUri(PropertyIdValue propertyIdValue) {
		String datatype = this.propertyRegister
				.getPropertyType(propertyIdValue);

		if (datatype == null)
			return null;

		switch (datatype) {
		case DatatypeIdValue.DT_STRING:
		case DatatypeIdValue.DT_EXTERNAL_ID:
		case DatatypeIdValue.DT_MATH:
			this.rdfConversionBuffer.addDatatypeProperty(propertyIdValue);
			return Vocabulary.XSD_STRING;
		case DatatypeIdValue.DT_COMMONS_MEDIA:
		case DatatypeIdValue.DT_GLOBE_COORDINATES:
		case DatatypeIdValue.DT_ITEM:
		case DatatypeIdValue.DT_PROPERTY:
		case DatatypeIdValue.DT_QUANTITY:
		case DatatypeIdValue.DT_TIME:
		case DatatypeIdValue.DT_URL:
			this.rdfConversionBuffer.addObjectProperty(propertyIdValue);
			return Vocabulary.OWL_THING;
		default:
			return null;
		}
	}

	/**
	 * Adds the given some-value restriction to the list of restrictions that
	 * should still be serialized. The given resource will be used as a subject.
	 *
	 * @param subject
	 * @param propertyUri
	 * @param rangeUri
	 */
	void addSomeValuesRestriction(Resource subject, String propertyUri,
			String rangeUri) {
		this.someValuesQueue.add(new PropertyRestriction(subject, propertyUri,
				rangeUri));
	}

	/**
	 * Adds the given no-value restriction to the list of restrictions that
	 * should still be serialized. The given resource will be used as a subject.
	 *
	 * @param subject
	 * @param propertyUri
	 * @param rangeUri
	 */
	void addNoValuesRestriction(Resource subject, String propertyUri,
			String rangeUri) {
		this.noValuesQueue.add(new PropertyRestriction(subject, propertyUri,
				rangeUri));
	}
}
