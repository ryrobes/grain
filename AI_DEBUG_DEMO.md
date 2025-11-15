# AI-Powered Debug UI Demo

Test the Grain Debug UI with real AI behavior trees using DSPy and OpenRouter.

## Prerequisites

- OPENROUTER_API_KEY environment variable set
- Debug UI running on port 8082
- Backend running on port 8080

## Quick Start

### Terminal 1: Start Backend

```bash
cd /home/ryanr/repos/grain
export OPENROUTER_API_KEY="your-key-here"  # If not already set
clj -A:dev
```

In the REPL:
```clojure
(require '[ai.obney.grain.debug-example-base.core :as demo])
(def app (demo/start))
```

### Terminal 2: Start Debug UI

```bash
cd /home/ryanr/repos/grain/ui/debug-ui
npm run dev
```

Open http://localhost:8082

### Run AI Commands

In the REPL:
```clojure
(require '[ai.obney.grain.debug-example-base.helpers :as h])

;; Run AI demo (3 commands in sequence)
(h/demo-ai app)
```

## AI Commands Available

### 1. Question & Answer

Ask any question and get an AI-generated answer with reasoning:

```clojure
(h/run-ai app :ai-question-answer
  {:question "Explain quantum entanglement in simple terms"})

(h/run-ai app :ai-question-answer
  {:question "What are the benefits of functional programming?"})
```

**What to see in Debug UI:**
- DSPy action node showing LLM call
- Chain-of-thought reasoning in events
- Answer and reasoning in memory
- Timing for LLM API call

### 2. Story Generator

Generate creative stories based on genre and characters:

```clojure
(h/run-ai app :ai-story-generator
  {:genre "cyberpunk"
   :characters ["Nova" "Cipher" "The Oracle"]})

(h/run-ai app :ai-story-generator
  {:genre "fantasy"
   :characters ["Gandalf" "Frodo" "Aragorn"]})

(h/run-ai app :ai-story-generator
  {:genre "detective noir"
   :characters ["Detective Stone" "The Widow"]})
```

**What to see in Debug UI:**
- Story generation with creative output
- Longer LLM response times
- Rich reasoning traces
- Full story text in memory

### 3. Recipe Suggester

Get recipe suggestions based on available ingredients:

```clojure
(h/run-ai app :ai-recipe-suggester
  {:items ["chicken" "coconut milk" "curry paste" "rice" "lime"]})

(h/run-ai app :ai-recipe-suggester
  {:items ["tomatoes" "pasta" "basil" "mozzarella" "olive oil"]})

(h/run-ai app :ai-recipe-suggester
  {:items ["eggs" "bacon" "cheese" "bread"]})
```

**What to see in Debug UI:**
- Recipe title, ingredients, instructions
- AI reasoning about ingredient combinations
- Structured output (title, ingredients list, instructions)
- Event emission with recipe data

## Debug UI Features to Explore with AI

### DSPy Annotations

The debug UI shows special annotations for DSPy calls:

- **DSPy Call Start** - When LLM request begins
- **DSPy Call Complete** - When response received
- **Reasoning** - Chain-of-thought reasoning from AI
- **Timing** - See how long LLM calls take (usually 1-3 seconds)

### Memory Inspector

Watch AI outputs appear in memory:
- `:answer` - AI response
- `:reasoning` - Chain of thought
- `:story` - Generated story
- `:recipe_title`, `:recipe_ingredients`, `:recipe_instructions`

### Event Log

See DSPy-specific events:
- `dspy-call-start` - LLM request initiated
- `dspy-call-complete` - LLM response received
- Signature inputs/outputs
- API timing metrics

### Timeline Visualization

AI calls will show as longer duration bars in the timeline due to network latency.

## Example Session

```clojure
;; Start the app
(require '[ai.obney.grain.debug-example-base.core :as demo])
(require '[ai.obney.grain.debug-example-base.helpers :as h])
(def app (demo/start))

;; Run a simple question
(h/run-ai app :ai-question-answer
  {:question "What is the Grain framework?"})

;; Generate a story
(h/run-ai app :ai-story-generator
  {:genre "space opera"
   :characters ["Captain Aria" "The Android" "Professor Kepler"]})

;; Get a recipe
(h/run-ai app :ai-recipe-suggester
  {:items ["salmon" "lemon" "dill" "potatoes" "butter"]})

;; Run the full demo
(h/demo-ai app)
```

## What Makes This Interesting for Debug UI

1. **Real AI Latency** - See actual network calls to OpenRouter (1-3 seconds)
2. **Complex State** - AI outputs stored in memory, visible in inspector
3. **Event Sourcing** - All AI interactions persisted as events
4. **Chain of Thought** - See AI reasoning process in the trace
5. **Structured Outputs** - DSPy ensures valid JSON schemas
6. **Error Handling** - If AI fails, see fallback behavior

## Troubleshooting

### DSPy Configuration Error

If you see:
```
ExceptionInfo: OPENROUTER_API_KEY environment variable not set
```

Set your API key:
```bash
export OPENROUTER_API_KEY="sk-or-v1-..."
```

Then restart the REPL.

### Python/libpython-clj Errors

DSPy requires Python integration. If you see Python errors:

1. Check Python is installed: `python3 --version`
2. Install DSPy: `pip install dspy-ai`
3. Restart the REPL

### Slow Response Times

Normal! LLM API calls take 1-5 seconds depending on:
- Model size (gpt-4o-mini is faster than gpt-4)
- Response length
- Network latency
- OpenRouter queue

### Schema Validation Errors

If command fails with schema errors, check that:
- `:question` is a non-empty string
- `:genre` is a string
- `:characters` is a vector of strings
- `:items` is a vector of strings

## Advanced Usage

### Run Multiple AI Commands in Parallel

```clojure
(require '[clojure.core.async :as async])

;; Fire off multiple AI commands
(doseq [q ["What is Clojure?"
           "Explain event sourcing"
           "What are behavior trees?"]]
  (async/thread
    (h/run-ai app :ai-question-answer {:question q})))
```

Watch all three traces appear in the debug UI simultaneously!

### Custom AI Commands

Create your own:

1. Define a signature in `signatures.clj`
2. Create a behavior tree using `dspy` action
3. Add command handler
4. Register schema
5. Test with `h/run-ai`

## Performance Notes

- **Question answering**: ~1-2 seconds
- **Story generation**: ~2-4 seconds (longer responses)
- **Recipe suggestions**: ~2-3 seconds
- **Chain-of-thought**: Adds 20-50% to latency but improves quality

## Next Steps

After testing the AI features, try:

1. **Modify prompts** in `signatures.clj` to change AI behavior
2. **Add new signatures** for different AI tasks
3. **Create multi-step AI workflows** with multiple DSPy calls
4. **Combine AI with decision logic** (conditions, fallbacks)
5. **Integrate into your own Grain app**

## License

Same as Grain framework.
