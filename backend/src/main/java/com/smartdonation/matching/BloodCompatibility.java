package com.smartdonation.matching;

import com.smartdonation.entity.BloodGroup;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class BloodCompatibility {
    private static final Map<BloodGroup, Set<BloodGroup>> COMPATIBLE_DONORS = Map.of(
            BloodGroup.A_POSITIVE, Set.of(BloodGroup.A_POSITIVE, BloodGroup.A_NEGATIVE, BloodGroup.O_POSITIVE, BloodGroup.O_NEGATIVE),
            BloodGroup.A_NEGATIVE, Set.of(BloodGroup.A_NEGATIVE, BloodGroup.O_NEGATIVE),
            BloodGroup.B_POSITIVE, Set.of(BloodGroup.B_POSITIVE, BloodGroup.B_NEGATIVE, BloodGroup.O_POSITIVE, BloodGroup.O_NEGATIVE),
            BloodGroup.B_NEGATIVE, Set.of(BloodGroup.B_NEGATIVE, BloodGroup.O_NEGATIVE),
            BloodGroup.AB_POSITIVE, EnumSet.allOf(BloodGroup.class),
            BloodGroup.AB_NEGATIVE, Set.of(BloodGroup.A_NEGATIVE, BloodGroup.B_NEGATIVE, BloodGroup.AB_NEGATIVE, BloodGroup.O_NEGATIVE),
            BloodGroup.O_POSITIVE, Set.of(BloodGroup.O_POSITIVE, BloodGroup.O_NEGATIVE),
            BloodGroup.O_NEGATIVE, Set.of(BloodGroup.O_NEGATIVE)
    );

    public boolean canDonate(BloodGroup donor, BloodGroup recipient) {
        return donor != null && recipient != null && COMPATIBLE_DONORS.get(recipient).contains(donor);
    }
}
