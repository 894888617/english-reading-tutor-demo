export interface Sentence {
  english: string;
  chinese: string;
}

export interface StoryPage {
  page: number;
  sentences: Sentence[];
}

export interface StoryResponse {
  title: string;
  englishTitle: string;
  level: string;
  pages: StoryPage[];
}

export const fallbackStory: StoryResponse = {
  title: '小兔子',
  englishTitle: 'The Little Rabbit',
  level: '初学者',
  pages: [
    {
      page: 1,
      sentences: [
        {
          english: 'The little rabbit is looking for his red hat.',
          chinese: '小兔子正在找他的红帽子。',
        },
        {
          english: 'He asks the bird, have you seen my hat?',
          chinese: '他问鸟儿：你见过我的帽子吗？',
        },
      ],
    },
    {
      page: 2,
      sentences: [
        {
          english: 'The bird says, look under the tree.',
          chinese: '鸟儿说：看看树下面。',
        },
        {
          english: 'The rabbit finds his red hat.',
          chinese: '小兔子找到了他的红帽子。',
        },
      ],
    },
  ],
};
