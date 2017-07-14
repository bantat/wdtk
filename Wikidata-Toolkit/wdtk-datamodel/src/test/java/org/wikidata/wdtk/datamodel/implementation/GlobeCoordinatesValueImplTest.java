package org.wikidata.wdtk.datamodel.implementation;

/*
 * #%L
 * Wikidata Toolkit Data Model
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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.wikidata.wdtk.datamodel.interfaces.GlobeCoordinatesValue;

public class GlobeCoordinatesValueImplTest {

	GlobeCoordinatesValue c1;
	GlobeCoordinatesValue c2;

	@Before
	public void setUp() throws Exception {
		c1 = new GlobeCoordinatesValueImpl(12.3, 14.1,
				GlobeCoordinatesValue.PREC_DEGREE,
				GlobeCoordinatesValue.GLOBE_EARTH);
		c2 = new GlobeCoordinatesValueImpl(12.3, 14.1,
				GlobeCoordinatesValue.PREC_DEGREE,
				GlobeCoordinatesValue.GLOBE_EARTH);
	}

	@Test
	public void dataIsCorrect() {
		assertEquals(c1.getLatitude(), 12.3, 0);
		assertEquals(c1.getLongitude(), 14.1, 0);
		assertEquals(c1.getPrecision(), GlobeCoordinatesValue.PREC_DEGREE, 0);
		assertEquals(c1.getGlobe(), GlobeCoordinatesValue.GLOBE_EARTH);
	}

	@Test
	public void equalityBasedOnContent() {
		GlobeCoordinatesValue gcDiffLatitude = new GlobeCoordinatesValueImpl(
				12.1, 14.1, GlobeCoordinatesValue.PREC_DEGREE,
				GlobeCoordinatesValue.GLOBE_EARTH);
		GlobeCoordinatesValue gcDiffLongitude = new GlobeCoordinatesValueImpl(
				12.3, 14.2, GlobeCoordinatesValue.PREC_DEGREE,
				GlobeCoordinatesValue.GLOBE_EARTH);
		GlobeCoordinatesValue gcDiffPrecision = new GlobeCoordinatesValueImpl(
				12.3, 14.1, GlobeCoordinatesValue.PREC_MILLI_ARCSECOND,
				GlobeCoordinatesValue.GLOBE_EARTH);
		GlobeCoordinatesValue gcDiffGlobe = new GlobeCoordinatesValueImpl(12.3,
				14.1, GlobeCoordinatesValue.PREC_DEGREE,
				"http://wikidata.org/entity/Q367221");

		assertEquals(c1, c1);
		assertEquals(c1, c2);
		assertThat(c1, not(equalTo(gcDiffLatitude)));
		assertThat(c1, not(equalTo(gcDiffLongitude)));
		assertThat(c1, not(equalTo(gcDiffPrecision)));
		assertThat(c1, not(equalTo(gcDiffGlobe)));
		assertThat(c1, not(equalTo(null)));
		assertFalse(c1.equals(this));
	}

	@Test
	public void hashBasedOnContent() {
		assertEquals(c1.hashCode(), c2.hashCode());
	}

	@Test(expected = NullPointerException.class)
	public void globeNotNull() {
		new GlobeCoordinatesValueImpl(12.3, 14.1,
				GlobeCoordinatesValue.PREC_DEGREE, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void onlyAllowedPrecisions() {
		new GlobeCoordinatesValueImpl(12.3, 14.1, 0.0,
				GlobeCoordinatesValue.GLOBE_EARTH);
	}

	@Test(expected = IllegalArgumentException.class)
	public void latitudeWithinUpperRange() {
		new GlobeCoordinatesValueImpl(91.0, 270.0,
				GlobeCoordinatesValue.PREC_DEGREE,
				GlobeCoordinatesValue.GLOBE_EARTH);
	}

	@Test(expected = IllegalArgumentException.class)
	public void latitudeWithinLowerRange() {
		new GlobeCoordinatesValueImpl(-91.0, 270.0,
				GlobeCoordinatesValue.PREC_DEGREE,
				GlobeCoordinatesValue.GLOBE_EARTH);
	}

	@Test(expected = IllegalArgumentException.class)
	public void longitudeWithinUpperRange() {
		new GlobeCoordinatesValueImpl(45.0, 500.0,
				GlobeCoordinatesValue.PREC_DEGREE,
				GlobeCoordinatesValue.GLOBE_EARTH);
	}

	@Test(expected = IllegalArgumentException.class)
	public void longitudeWithinLowerRange() {
		new GlobeCoordinatesValueImpl(45.0, -500.0,
				GlobeCoordinatesValue.PREC_DEGREE,
				GlobeCoordinatesValue.GLOBE_EARTH);
	}

}
