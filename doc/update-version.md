# Upgrading from 1.15.x

## Moved configuration keys
```yaml
goal:
  time:
    timedend: 0
    winner: "none"
#becomes
general:
  timer:
    end: 0
    winner: "none"
```

```yaml
goals:
  - PlayerLives
#becomes
general:
  goal: PlayerLives
```

```yaml
goal:
  livesPerPlayer: false
#becomes
general:
  addLivesPerPlayer: false
```

```yaml
goal:
  endCountDown: 5
#becomes
time:
  endCountDown: 5
```

```yaml
general:
  time: -1
#becomes
player:
  time: -1
```

```yaml
general:
  gm: 0
#becomes
general:
  gamemode: SURVIVAL
```