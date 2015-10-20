Known facts:

- Parent filters state of children
- Walking into a component with query causes props 
# Application state structure:

Visualized application state structure:

```
app-state {
    :toolbox { ... all the stuff in the toolbox ... }
    :dashboard { ... all the stuff in the dashboard ... }
    :menu { ... the overall menu ... }
    }
```

Top-level query:

```
    [ {:toolbox (om/get-query Toolbox)} { :dashboard (om/get-query Dashboard) } {:menu (om/get-query Menu) } ]
```

Toolbox query:
 
```
   [ { :widgets [:widget/title :widget/action] } ]
```

