package uk.ac.ebi.owl2json.properties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import uk.ac.ebi.owl2json.OwlTranslator;

public class PropertySet {

    private Map<String, List<PropertyValue>> properties = new TreeMap<>();

    public void addProperty(String predicate, PropertyValue value) {
        List<PropertyValue> props = properties.get(predicate);
        if (props != null) {

    // prevent dupliacte values if same triple appears in multiple owl files
            for(PropertyValue p : props) {
                if(p.equals(value)) {
                    return;
                }
            }

            props.add(value);
        } else {
            props = new ArrayList<>();
            props.add(value);
            properties.put(predicate, props);
        }
    }

    public boolean hasProperty(String predicate) {
        return properties.containsKey(predicate);
    }

    public void annotateProperty(String predicate, PropertyValue value, String predicate2, PropertyValue value2,
            OwlTranslator translator) {

        List<PropertyValue> props = properties.get(predicate);

        PropertyValue prop = null;

        if (props != null) {

            if (value.getType() == PropertyValue.Type.BNODE) {
                // bnode case, look for an isomorphic bnode
                for (PropertyValue existingValue : props) {
                    if (existingValue.getType() == PropertyValue.Type.BNODE) {
                        if (translator.areSubgraphsIsomorphic(existingValue, value)) {
                            prop = existingValue;
                            break;
                        }
                    }
                }
            } else {
                // simple case, look for an equal value to reify
                for (PropertyValue p : props) {
                    if (p.equals(value)) {
                        prop = p;
                        break;
                    }
                }
            }
            if (prop == null) {
                prop = value;
                props.add(prop);
            }
        } else {
            props = new ArrayList<>();
            prop = value;
            props.add(prop);
            properties.put(predicate, props);
        }

        if (prop.properties == null) {
            prop.properties = new PropertySet();
        }

        prop.properties.addProperty(predicate2, value2);
    }

    public Set<String> getPropertyPredicates() {
        return properties.keySet();
    }

    public List<PropertyValue> getPropertyValues(String predicate) {
        return properties.get(predicate);
    }

    public PropertyValue getPropertyValue(String predicate) {
        List<PropertyValue> values = properties.get(predicate);
        if(values.size() == 0) {
            return null;
        }
        if(values.size() == 1) {
            return values.get(0);
        }
        throw new RuntimeException("More than one property value for getOne: " + predicate);
    }

    public void removeProperty(String predicate) {
        properties.remove(predicate);
    }


}
