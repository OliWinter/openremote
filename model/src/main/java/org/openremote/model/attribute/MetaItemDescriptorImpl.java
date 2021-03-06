/*
 * Copyright 2017, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.model.attribute;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.openremote.model.ValidationFailure;
import org.openremote.model.value.Value;
import org.openremote.model.value.ValueType;

import java.util.Optional;
import java.util.function.Function;

public class MetaItemDescriptorImpl implements MetaItemDescriptor {

    protected final String name;
    protected final String urn;
    protected final ValueType valueType;
    protected final boolean required;
    protected final String pattern;
    protected final Integer maxPerAttribute;
    protected final Value initialValue;
    protected final boolean valueFixed;
    protected final String patternFailureMessage;

    @JsonCreator
    public MetaItemDescriptorImpl(@JsonProperty("name") String name,
                                  @JsonProperty("urn") String urn,
                                  @JsonProperty("valueType") ValueType valueType,
                                  @JsonProperty("required") boolean required,
                                  @JsonProperty("pattern") String pattern,
                                  @JsonProperty("patternFailureMessage") String patternFailureMessage,
                                  @JsonProperty("maxPerAttribute") Integer maxPerAttribute,
                                  @JsonProperty("initialValue") Value initialValue,
                                  @JsonProperty("valueFixed") boolean valueFixed) {
        this.name = name;
        this.urn = urn;
        this.valueType = valueType;
        this.required = required;
        this.pattern = pattern;
        this.patternFailureMessage = patternFailureMessage;
        this.maxPerAttribute = maxPerAttribute;
        this.initialValue = initialValue;
        this.valueFixed = valueFixed;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String getUrn() {
        return urn;
    }

    @Override
    public ValueType getValueType() {
        return valueType;
    }

    @Override
    public boolean isRequired() {
        return required;
    }

    @Override
    public String getPattern() {
        return pattern;
    }

    @Override
    public String getPatternFailureMessage() {
        return patternFailureMessage;
    }

    @Override
    public Integer getMaxPerAttribute() {
        return maxPerAttribute;
    }

    @Override
    public Value getInitialValue() {
        return initialValue;
    }

    @Override
    public boolean isValueFixed() {
        return valueFixed;
    }

    @JsonIgnore
    @Override
    public Optional<Function<Value, Optional<ValidationFailure>>> getValidator() {
        return Optional.empty();
    }
}
