# MigLayoutModel Best Practices Guide

## Table of Contents
- [Introduction](#introduction)
- [Core Concepts](#core-concepts)
- [Weight System](#weight-system)
- [Constraint Specifications](#constraint-specifications)
- [Common Patterns](#common-patterns)
- [Practical Examples](#practical-examples)
- [Debugging Tips](#debugging-tips)

## Introduction

`MigLayoutModel` is the layout management system used in Weasis by viewer or editor plugins that leverages MigLayout's flexible constraint-based approach. This guide covers best practices for using weights, constraints, and sizing to create responsive, maintainable UI layouts.

## Core Concepts

### What is MigLayoutModel?

MigLayoutModel is a grid-based layout system that allows you to:
- Define rows and columns with flexible sizing behavior
- Control how components grow and shrink when the container resizes
- Set minimum, preferred, and maximum sizes for layout cells
- Create complex, responsive layouts with simple declarative syntax

### The Grid System

Layouts are organized in a grid with:
- **Rows**: Horizontal divisions (configured with row constraints)
- **Columns**: Vertical divisions (configured with column constraints)
- **Cells**: Individual spaces where components are placed

## Weight System

Weights control how space is distributed among rows and columns when the container changes size.

### 1. Grow Weight (Default: 100)

**Purpose**: Controls space distribution when the container expands.

**Behavior**:
- Higher weight = receives more extra space
- Weight 0 = fixed size (won't grow beyond preferred size)
- Weights are proportional: 1:2:1 ratio = weights of 100:200:100

**Example**:
```java
// Three columns growing in 1:2:1 ratio
double[] columnWeights = {100, 200, 100};
model.setColumnWeights(columnWeights);
```

**Use Cases**:
- Main content area: high weight (100-200)
- Sidebars or toolbars: low weight (0-50) or fixed (0)
- Equal distribution: all weights equal (100:100:100)

### 2. Shrink Weight (Default: 100)

**Purpose**: Controls space reduction when the container shrinks.

**Behavior**:
- Higher weight = loses more space when container shrinks
- Weight 0 = won't shrink below preferred size
- Independent from grow weight (can grow but not shrink, or vice versa)

**Example**:
```java
// First column shrinks slowly, second shrinks quickly
double[] growWeights = {100, 100};
double[] shrinkWeights = {25, 100};
model.setColumnWeights(growWeights, shrinkWeights);
```

**Use Cases**:
- Critical content: low shrink weight (0-25)
- Flexible content: high shrink weight (100)
- Prevent compression: shrink weight 0

### 3. Fill Property

**Purpose**: Controls whether components fill the entire cell space.

**Behavior**:
- `true`: Component expands to fill entire cell
- `false`: Component uses its preferred size and alignment

**Use Cases**:
- Containers and panels: typically `true`
- Buttons and labels: typically `false` (unless specific design requires)
- Images and viewers: typically `true`

## Constraint Specifications

`ConstraintSpec` objects provide fine-grained control over cell sizing.

### Constructor Parameters

```java
ConstraintSpec(
    double growWeight,      // How much it grows (0 = no growth)
    double shrinkWeight,    // How much it shrinks (0 = no shrink)
    boolean fill,           // Fill cell space?
    Integer minSize,        // Minimum size in pixels (null = no limit)
    Integer preferredSize,  // Preferred size in pixels (null = component default)
    Integer maxSize         // Maximum size in pixels (null = no limit)
)
```

### Factory Methods

#### Fixed Size
```java
// Fixed 200px (no grow, no shrink)
ConstraintSpec.fixed(200)
```

#### Size Bounds
```java
// Min, preferred, max sizes
ConstraintSpec.withBounds(100, 200, 300)
```

### Size Bounds Explained

**Format**: `min:preferred:max`

- **Min**: Minimum size in pixels (null = no minimum, uses 0)
- **Preferred**: Ideal size when no extra space available (null = component's preferred size)
- **Max**: Maximum size in pixels (null = no maximum, can grow indefinitely)

**Examples**:
```java
// Minimum 300px, can grow indefinitely
new ConstraintSpec(100, 100, true, 300, null, null)

// Preferred 200px, max 500px
new ConstraintSpec(100, 100, true, null, 200, 500)

// Fixed between 100-300px with preferred 200px
ConstraintSpec.withBounds(100, 200, 300)
```

## Common Patterns

### 1. Fixed Sidebar
```java
// Left sidebar: fixed 200px (no grow/shrink)
ConstraintSpec.fixed(200)
```
**Use for**: Toolbars, navigation panels, property inspectors

### 2. Flexible Content Area
```java
// Content area: grows and shrinks freely
new ConstraintSpec(100, 100)
```
**Use for**: Main content area, document viewers, canvas

### 3. Minimum Size Constraint
```java
// Grows freely but won't shrink below 300px
new ConstraintSpec(100, 0, true, 300, null, null)
```
**Use for**: Content that needs minimum space to be usable

### 4. Maximum Size Constraint
```java
// Grows up to 500px maximum
new ConstraintSpec(100, 100, true, null, null, 500)
```
**Use for**: Preventing elements from becoming too large

### 5. Bounded Size Range
```java
// Constrained between 100-300px
ConstraintSpec.withBounds(100, 200, 300)
```
**Use for**: Sidebars that should stay within reasonable bounds

### 6. No Shrink Layout
```java
// Grows but never shrinks below preferred size
double[] growWeights = {100, 100, 100};
double[] shrinkWeights = {0, 0, 0};
model.setColumnWeights(growWeights, shrinkWeights);
```
**Use for**: Layouts where minimum content visibility is critical

## Practical Examples

### Example 1: Proportional Growth (1:2:1 Ratio)

Three columns where the middle column gets twice as much extra space.

```java
public static MigLayoutModel createProportionalGrowthLayout() {
    MigLayoutModel model = new MigLayoutModel(
        "example1",
        "Proportional Growth",
        2, 3,  // 2 rows, 3 columns
        "org.weasis.core.ui.editor.image.DefaultView2d"
    );
    
    double[] columnWeights = {100, 200, 100};
    model.setColumnWeights(columnWeights);
    
    return model;
}
```

**Result**: When window expands, middle column grows twice as fast as side columns.

### Example 2: Fixed Sidebar with Flexible Content

Classic application layout with fixed left sidebar.

```java
public static MigLayoutModel createFixedSidebarLayout() {
    MigLayoutModel model = new MigLayoutModel(
        "example3",
        "Fixed Sidebar",
        1, 3,  // 1 row, 3 columns
        "org.weasis.core.ui.editor.image.DefaultView2d"
    );
    
    ConstraintSpec[] columnSpecs = {
        ConstraintSpec.fixed(200),      // Left sidebar: 200px
        new ConstraintSpec(100, 100),   // Middle: flexible
        ConstraintSpec.fixed(150)       // Right sidebar: 150px
    };
    
    model.setColumnConstraintSpecs(columnSpecs);
    return model;
}
```

**Result**: Sidebars stay constant, all resizing affects middle column.

### Example 3: Toolbar with Content Area

Vertical layout with fixed toolbar height.

```java
public static MigLayoutModel createToolbarLayout() {
    MigLayoutModel model = new MigLayoutModel(
        "example5",
        "Toolbar Layout",
        2, 1,  // 2 rows, 1 column
        "org.weasis.core.ui.editor.image.DefaultView2d"
    );
    
    ConstraintSpec[] rowSpecs = {
        ConstraintSpec.fixed(40),       // Toolbar: 40px
        new ConstraintSpec(100, 100)    // Content: flexible
    };
    
    model.setRowConstraintSpecs(rowSpecs);
    return model;
}
```

**Result**: Toolbar height fixed, content area grows/shrinks with window.

### Example 4: Complex Grid (Header/Footer/Sidebar)

3x3 grid with fixed header, footer, and sidebars.

```java
public static MigLayoutModel createComplexGridLayout() {
    MigLayoutModel model = new MigLayoutModel(
        "example6",
        "Complex Grid",
        3, 3,  // 3 rows, 3 columns
        "org.weasis.core.ui.editor.image.DefaultView2d"
    );
    
    // Columns: fixed left (200px), flexible middle, fixed right (150px)
    ConstraintSpec[] columnSpecs = {
        ConstraintSpec.fixed(200),
        new ConstraintSpec(100, 100),
        ConstraintSpec.fixed(150)
    };
    model.setColumnConstraintSpecs(columnSpecs);
    
    // Rows: fixed header (50px), flexible content, fixed footer (30px)
    ConstraintSpec[] rowSpecs = {
        ConstraintSpec.fixed(50),
        new ConstraintSpec(100, 100),
        ConstraintSpec.fixed(30)
    };
    model.setRowConstraintSpecs(rowSpecs);
    
    return model;
}
```

**Result**: Only center cell (middle row, middle column) resizes.

### Example 5: Image Viewer with Control Panel

Main image area grows freely, control panel has limited growth.

```java
public static MigLayoutModel createImageViewerLayout() {
    MigLayoutModel model = new MigLayoutModel(
        "example7",
        "Image Viewer",
        1, 2,  // 1 row, 2 columns
        "org.weasis.core.ui.editor.image.DefaultView2d"
    );
    
    ConstraintSpec[] columnSpecs = {
        // Image area: grows/shrinks freely, min 300px
        new ConstraintSpec(100, 100, true, 300, null, null),
        
        // Control panel: limited growth, preferred 250px
        new ConstraintSpec(20, 50, true, 200, 250, 300)
    };
    
    model.setColumnConstraintSpecs(columnSpecs);
    return model;
}
```

**Result**: Image gets most space, controls stay compact (200-300px range).

### Example 6: Form Layout

Labels with fixed width, input fields that grow.

```java
public static MigLayoutModel createFormLayout() {
    MigLayoutModel model = new MigLayoutModel(
        "example9",
        "Form Layout",
        4, 2,  // 4 rows, 2 columns
        "org.weasis.core.ui.editor.image.DefaultView2d"
    );
    
    ConstraintSpec[] columnSpecs = {
        // Label column: fixed 120px
        new ConstraintSpec(0, 0, true, 120, 120, 120),
        
        // Field column: grows to fill
        new ConstraintSpec(100, 100)
    };
    
    model.setColumnConstraintSpecs(columnSpecs);
    return model;
}
```

**Result**: Labels stay aligned at 120px, fields use remaining space.

### Example 7: Asymmetric Shrink Behavior

Different shrink priorities for columns.

```java
public static MigLayoutModel createAsymmetricShrinkLayout() {
    MigLayoutModel model = new MigLayoutModel(
        "example2",
        "Asymmetric Shrink",
        2, 2,  // 2 rows, 2 columns
        "org.weasis.core.ui.editor.image.DefaultView2d"
    );
    
    double[] growWeights = {100, 100};      // Both grow equally
    double[] shrinkWeights = {25, 100};     // First shrinks slowly
    model.setColumnWeights(growWeights, shrinkWeights);
    
    return model;
}
```

**Result**: When shrinking, second column loses space 4x faster than first.

## Dynamic Constraint Updates

You can modify constraints at runtime to respond to user actions or application state.

### Equal Distribution
```java
double[] equalWeights = new double[model.getGridSize().width];
Arrays.fill(equalWeights, 100);
model.setColumnWeights(equalWeights);
```

### Focus on Specific Column
```java
double[] focusedWeights = new double[model.getGridSize().width];
focusedWeights[0] = 200;  // First column emphasized
Arrays.fill(focusedWeights, 1, focusedWeights.length, 50);
model.setColumnWeights(focusedWeights);
```

### Protect Important Columns
```java
double[] growWeights = new double[cols];
double[] shrinkWeights = new double[cols];
Arrays.fill(growWeights, 100);
Arrays.fill(shrinkWeights, 100);
shrinkWeights[0] = 0;  // First column won't shrink
model.setColumnWeights(growWeights, shrinkWeights);
```

## Debugging Tips

### 1. Enable Debug Mode
When testing layouts, enable MigLayout's debug visualization:
```java
// Note: This would be in the actual MigLayout instantiation
new MigLayout("debug", ...)
```

### 2. Test Resize Behavior
Always test:
- **Expansion**: Does space distribute as expected?
- **Contraction**: Do important elements maintain minimum sizes?
- **Extreme sizes**: What happens at very small/large window sizes?

### 3. Check Size Bounds
Verify that:
- Minimum sizes prevent content from becoming unusable
- Maximum sizes prevent elements from dominating the layout
- Preferred sizes reflect actual content needs

### 4. Validate Weight Ratios
- Use simple ratios (1:2:1, 1:3:1) rather than complex numbers
- Document the intended behavior in comments
- Test that the visual result matches expectations

### 5. Common Issues

**Problem**: Column won't grow
- **Solution**: Check grow weight is > 0
- **Solution**: Verify no max size constraint limiting growth

**Problem**: Column shrinks too much
- **Solution**: Set shrink weight to 0 or low value
- **Solution**: Add minimum size constraint

**Problem**: Layout doesn't respond to resize
- **Solution**: Check that fill=true for container components
- **Solution**: Verify parent container has proper layout

**Problem**: Unequal distribution despite equal weights
- **Solution**: Check for size constraints (min/max) affecting distribution
- **Solution**: Ensure components have appropriate preferred sizes

## Best Practices Summary

### DO:
✅ Use proportional weights (1:2:1) for clear relationships  
✅ Set shrink weight to 0 for critical content  
✅ Use fixed sizes for toolbars, headers, and sidebars  
✅ Set minimum sizes to ensure usability  
✅ Test resize behavior in both directions  
✅ Document complex weight configurations  
✅ Use factory methods (fixed(), withBounds()) for clarity  

### DON'T:
❌ Use arbitrary large numbers for weights (keep them simple)  
❌ Forget to test shrinking behavior  
❌ Set all shrink weights to 0 (creates rigid layouts)  
❌ Overuse maximum size constraints  
❌ Mix weights and fixed sizes without careful testing  
❌ Assume default weights work for all scenarios  

## Conclusion

MigLayoutModel's weight system provides powerful, flexible layout control. By understanding grow weights, shrink weights, and size constraints, you can create responsive UIs that adapt gracefully to different window sizes while maintaining usability and visual hierarchy.

The key is to:
1. Start with simple weight ratios
2. Add size constraints where needed
3. Test thoroughly at different sizes
4. Document non-obvious layout decisions

For more examples, see: `org.weasis.core.ui.layout.MigLayoutWeightExample`

