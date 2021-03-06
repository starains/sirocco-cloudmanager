/**
 *
 * SIROCCO
 * Copyright (C) 2011 France Telecom
 * Contact: sirocco@ow2.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 *  $Id: $
 *
 */

package org.ow2.sirocco.cloudmanager.model.cimi.meter;

import java.io.Serializable;
import java.util.Date;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.ow2.sirocco.cloudmanager.model.cimi.CloudEntity;
import org.ow2.sirocco.cloudmanager.model.cimi.CloudResource;

@Entity
public class Meter extends CloudEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    protected CloudResource targetResource;

    protected String units;

    protected Integer sampleInterval;

    protected MeterConfiguration.TimeScope timeScope;

    protected MeterConfiguration.IntervalDuration intervalDuration;

    protected boolean isContinuous;

    protected Set<MeterSample> samples;

    protected String minValue;

    protected String maxValue;

    protected Date stopTime;

    protected Date expiresTime;

    @ManyToOne
    public CloudResource getTargetResource() {
        return this.targetResource;
    }

    public void setTargetResource(final CloudResource targetResource) {
        this.targetResource = targetResource;
    }

    public String getUnits() {
        return this.units;
    }

    public void setUnits(final String units) {
        this.units = units;
    }

    public Integer getSampleInterval() {
        return this.sampleInterval;
    }

    public void setSampleInterval(final Integer sampleInterval) {
        this.sampleInterval = sampleInterval;
    }

    @Enumerated(EnumType.STRING)
    public MeterConfiguration.TimeScope getTimeScope() {
        return this.timeScope;

    }

    public void setTimeScope(final MeterConfiguration.TimeScope timeScope) {
        this.timeScope = timeScope;
    }

    @Enumerated(EnumType.STRING)
    public MeterConfiguration.IntervalDuration getIntervalDuration() {
        return this.intervalDuration;
    }

    public void setIntervalDuration(final MeterConfiguration.IntervalDuration intervalDuration) {
        this.intervalDuration = intervalDuration;
    }

    public boolean isContinuous() {
        return this.isContinuous;
    }

    public void setContinuous(final boolean isContinuous) {
        this.isContinuous = isContinuous;
    }

    @OneToMany
    public Set<MeterSample> getSamples() {
        return this.samples;
    }

    public void setSamples(final Set<MeterSample> samples) {
        this.samples = samples;
    }

    @Column(name = "minValueMeter")
    public String getMinValue() {
        return this.minValue;
    }

    public void setMinValue(final String minValue) {
        this.minValue = minValue;
    }

    @Column(name = "maxValueMeter")
    public String getMaxValue() {
        return this.maxValue;

    }

    public void setMaxValue(final String maxValue) {
        this.maxValue = maxValue;
    }

    @Temporal(TemporalType.TIMESTAMP)
    public Date getStopTime() {
        return this.stopTime;
    }

    public void setStopTime(final Date stopTime) {
        this.stopTime = stopTime;
    }

    @Temporal(TemporalType.TIMESTAMP)
    public Date getExpiresTime() {
        return this.expiresTime;
    }

    public void setExpiresTime(final Date expiresTime) {
        this.expiresTime = expiresTime;
    }
}
