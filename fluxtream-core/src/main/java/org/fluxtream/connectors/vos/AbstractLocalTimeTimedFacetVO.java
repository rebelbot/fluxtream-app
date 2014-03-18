package org.fluxtream.connectors.vos;

import org.fluxtream.OutsideTimeBoundariesException;
import org.fluxtream.TimeInterval;
import org.fluxtream.domain.AbstractLocalTimeFacet;
import org.fluxtream.domain.GuestSettings;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

/**
 *
 * @author Candide Kemmler (candide@fluxtream.com)
 */
public abstract class AbstractLocalTimeTimedFacetVO<T extends AbstractLocalTimeFacet> extends AbstractLocalTimeInstantFacetVO<T> {

    public String eventEnd;

    @Override
    public void extractValues(T facet, TimeInterval timeInterval, GuestSettings settings)
            throws OutsideTimeBoundariesException
    {
        super.extractValues(facet, timeInterval, settings);
        this.eventStart = ISODateTimeFormat.basicDateTime().withZone(DateTimeZone.forTimeZone(timeInterval.getTimeZone(facet.date))).print(facet.start);
        this.eventEnd = ISODateTimeFormat.basicDateTime().withZone(DateTimeZone.forTimeZone(timeInterval.getTimeZone(facet.date))).print(facet.end);
    }
}
