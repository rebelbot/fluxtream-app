package org.fluxtream.connectors.vos;

import org.fluxtream.OutsideTimeBoundariesException;
import org.fluxtream.TimeInterval;
import org.fluxtream.domain.AbstractFacet;
import org.fluxtream.domain.GuestSettings;
import org.fluxtream.mvc.models.DurationModel;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

public abstract class AbstractTimedFacetVO<T extends AbstractFacet> extends AbstractInstantFacetVO<T> {

    public transient long end;
    public DurationModel duration;
    public String eventStart, eventEnd;

	@Override
	public void extractValues(T facet, TimeInterval timeInterval, GuestSettings settings) throws OutsideTimeBoundariesException {
		super.extractValues(facet, timeInterval, settings);
        this.eventStart = ISODateTimeFormat.basicDateTime().withZone(DateTimeZone.forTimeZone(timeInterval.getTimeZone(facet.start))).print(facet.start);
        this.eventEnd = ISODateTimeFormat.basicDateTime().withZone(DateTimeZone.forTimeZone(timeInterval.getTimeZone(facet.end))).print(facet.end);
	}

    public long end(){
        return ISODateTimeFormat.basicDate().parseDateTime(this.eventEnd).getMillis();
    }

    public void setStart(final long millis) {
        this.eventStart = ISODateTimeFormat.basicDateTime().withZoneUTC().print(millis);
    }

    public void setEnd(final long millis) {
        this.eventEnd = ISODateTimeFormat.basicDateTime().withZoneUTC().print(millis);
    }
}
