(ns ai.obney.grain.debug-example-service.core.signatures
  "DSPy signatures for AI-powered debug examples."
  (:require [ai.obney.grain.clj-dspy.interface :refer [defsignature]]
            [ai.obney.grain.schema-util.interface :refer [defschemas]]))

;;
;; Schemas for DSPy Signatures
;;

(defschemas ai-schemas
  {::question [:string {:desc "User's question or prompt"}]
   ::answer [:string {:desc "AI generated answer"}]
   ::reasoning [:string {:desc "Chain of thought reasoning"}]
   ::story [:string {:desc "Generated story text"}]
   ::story-genre [:string {:desc "Genre of the story"}]
   ::characters [:vector {:desc "Character names"} :string]
   ::code [:string {:desc "Code snippet to review"}]
   ::feedback [:string {:desc "Code review feedback"}]
   ::suggestions [:vector {:desc "Improvement suggestions"} :string]
   ::items [:vector {:desc "List of items"} :string]
   ::recipe-title [:string {:desc "Recipe title"}]
   ::recipe-ingredients [:vector {:desc "Recipe ingredients"} :string]
   ::recipe-instructions [:string {:desc "Cooking instructions"}]
   ::sentiment [:string {:desc "Sentiment classification: positive, negative, or neutral"}]
   ::confidence [:double {:desc "Confidence score 0-1"}]
   ::text [:string {:desc "Text to analyze"}]})

;;
;; DSPy Signatures
;;

(defsignature QuestionAnswerer
  "You are a helpful AI assistant. Answer questions clearly, concisely, and accurately.
   Provide thoughtful reasoning for your answers."
  {:inputs {:question ::question}
   :outputs {:answer ::answer
             :reasoning ::reasoning}})

(defsignature StoryGenerator
  "You are a creative storytelling AI. Generate engaging short stories (2-3 paragraphs)
   based on the given genre and characters. Be imaginative and vivid in your descriptions."
  {:inputs {:genre ::story-genre
            :characters ::characters}
   :outputs {:story ::story
             :reasoning ::reasoning}})

(defsignature CodeReviewer
  "You are an expert code reviewer. Analyze the provided code snippet and provide
   constructive feedback on code quality, potential bugs, and best practices.
   Give 2-4 specific, actionable suggestions."
  {:inputs {:code ::code}
   :outputs {:feedback ::feedback
             :suggestions ::suggestions}})

(defsignature RecipeSuggester
  "You are a creative chef and recipe developer. Given a list of available ingredients,
   suggest a delicious recipe that uses most of them. Be creative but practical."
  {:inputs {:available_items ::items}
   :outputs {:recipe_title ::recipe-title
             :recipe_ingredients ::recipe-ingredients
             :recipe_instructions ::recipe-instructions
             :reasoning ::reasoning}})

(defsignature SentimentAnalyzer
  "You are a sentiment analysis expert. Analyze the given text and classify its
   overall sentiment as positive, negative, or neutral. Explain your reasoning."
  {:inputs {:text ::text}
   :outputs {:sentiment ::sentiment
             :confidence ::confidence
             :reasoning ::reasoning}})
